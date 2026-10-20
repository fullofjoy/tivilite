package com.tivilite.tv.mesh;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.tivilite.tv.model.Channel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TiviLite Universal Playback Handoff Engine (UPHP)
 * Powers seamless bi-directional push and pull session handoff across TVs,
 * Phones, Tablets, and Desktop PCs on the local network.
 */
public class UniversalHandoffManager {
    private static final String TAG = "UniversalHandoff";

    public static class NearbySession {
        public final String deviceId;
        public final String deviceName;
        public final String formFactor;
        public final String channelName;
        public final String channelUrl;
        public final long timestampMs;
        public final long detectedAt;

        public NearbySession(String deviceId, String deviceName, String formFactor, String channelName, String channelUrl, long timestampMs) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.formFactor = formFactor;
            this.channelName = channelName;
            this.channelUrl = channelUrl;
            this.timestampMs = timestampMs;
            this.detectedAt = System.currentTimeMillis();
        }

        public JSONObject toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("deviceId", deviceId);
                json.put("deviceName", deviceName);
                json.put("formFactor", formFactor);
                json.put("channelName", channelName);
                json.put("channelUrl", channelUrl);
                json.put("timestampMs", timestampMs);
                return json;
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    public interface HandoffListener {
        void onNearbySessionDetected(NearbySession session);
        void onIncomingHandoffReceived(Channel channel, long timestampMs);
    }

    private static volatile UniversalHandoffManager instance;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, NearbySession> activeSessions = new ConcurrentHashMap<>();
    private HandoffListener listener;

    public static UniversalHandoffManager getInstance(Context context) {
        if (instance == null) {
            synchronized (UniversalHandoffManager.class) {
                if (instance == null) {
                    instance = new UniversalHandoffManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private UniversalHandoffManager(Context context) {
        this.context = context;
        setupCloudMeshBridge();
    }

    public void setHandoffListener(HandoffListener listener) {
        this.listener = listener;
    }

    private void setupCloudMeshBridge() {
        CloudMeshManager.getInstance(context).setHandoffListener((chName, chUrl, pos) -> {
            try {
                if (chUrl != null && !chUrl.isEmpty()) {
                    Channel ch = new Channel(8888, chName != null ? chName : "接力频道", "📲 跨屏接力", chUrl);
                    handler.post(() -> {
                        if (listener != null) listener.onIncomingHandoffReceived(ch, pos);
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    public void updateMyPlayingState(Channel currentChannel, long positionMs) {
        if (currentChannel != null) {
            CloudMeshManager.getInstance(context).updatePlaybackState(currentChannel.getName(), currentChannel.getUrl(), positionMs);
        }
    }

    public void registerDiscoveredSession(NearbySession session) {
        activeSessions.put(session.deviceId, session);
        handler.post(() -> {
            if (listener != null) listener.onNearbySessionDetected(session);
        });
    }

    public List<NearbySession> getNearbySessions() {
        List<NearbySession> list = new ArrayList<>();
        List<CloudMeshManager.PeerDevice> peers = CloudMeshManager.getInstance(context).getActivePeers();
        long now = System.currentTimeMillis();
        for (CloudMeshManager.PeerDevice p : peers) {
            if (p.currentChannel != null && !p.currentChannel.isEmpty()
                    && p.currentUrl != null && !p.currentUrl.isEmpty()
                    && (now - p.lastSeen < 30000)) {
                list.add(new NearbySession(p.deviceId, p.deviceName, "TV/Terminal", p.currentChannel, p.currentUrl, p.currentPtsMs));
            }
        }
        for (NearbySession s : activeSessions.values()) {
            if (now - s.detectedAt < 60000) {
                list.add(s);
            }
        }
        return list;
    }

    public NearbySession getLatestNearbySession() {
        List<NearbySession> sessions = getNearbySessions();
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    public void pushHandoffToDevice(String targetDeviceId, Channel channel, long positionMs) {
        CloudMeshManager.getInstance(context).sendHandoffToPeer(targetDeviceId);
    }

    public JSONObject getStatusJson() {
        JSONObject json = new JSONObject();
        try {
            JSONArray arr = new JSONArray();
            for (NearbySession s : getNearbySessions()) {
                arr.put(s.toJson());
            }
            json.put("nearbySessions", arr);
            json.put("sessionCount", arr.length());
        } catch (Exception ignored) {}
        return json;
    }
}
