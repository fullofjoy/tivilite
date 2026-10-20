package com.tivilite.tv.mesh;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.tivilite.tv.license.LicenseManager;
import com.tivilite.tv.server.WebPushServer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TiviLite Cloud Mesh & Zero-Configuration P2P Collaboration Grid
 * Enables multi-room TVs and mobile clients on the same Wi-Fi/LAN to discover each other,
 * share playback state, and perform seamless stream handoff (客厅看一半，进卧室一键接力无缝续播).
 */
public class CloudMeshManager {
    private static final String TAG = "CloudMeshManager";
    private static final int MESH_UDP_PORT = 8889;
    private static final String PREF_NAME = "tivilite_mesh_prefs";
    private static final String KEY_DEVICE_CUSTOM_NAME = "device_custom_name";

    public static class PeerDevice {
        public final String deviceId;
        public final String name;
        public final String ip;
        public final int port;
        public String currentChannel;
        public String currentUrl;
        public long currentPtsMs;
        public long lastSeen;

        public PeerDevice(String deviceId, String name, String ip, int port) {
            this.deviceId = deviceId;
            this.name = name;
            this.ip = ip;
            this.port = port;
            this.lastSeen = System.currentTimeMillis();
        }

        public JSONObject toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("deviceId", deviceId);
                json.put("name", name);
                json.put("ip", ip);
                json.put("port", port);
                json.put("currentChannel", currentChannel != null ? currentChannel : "");
                json.put("currentPtsMs", currentPtsMs);
                json.put("lastSeen", lastSeen);
                return json;
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    public interface MeshHandoffListener {
        void onHandoffReceived(String channelName, String channelUrl, long ptsMs);
    }

    private static volatile CloudMeshManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, PeerDevice> activePeers = new ConcurrentHashMap<>();

    private DatagramSocket socket;
    private boolean isRunning = false;
    private MeshHandoffListener handoffListener;

    private String myDeviceId;
    private String myDeviceName;
    private String currentChannelName = "";
    private String currentChannelUrl = "";
    private long currentPts = 0;

    public static CloudMeshManager getInstance(Context context) {
        if (instance == null) {
            synchronized (CloudMeshManager.class) {
                if (instance == null) {
                    instance = new CloudMeshManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private CloudMeshManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.myDeviceId = LicenseManager.getDeviceId(context);
        this.myDeviceName = prefs.getString(KEY_DEVICE_CUSTOM_NAME, "客厅电视 (" + Build.MODEL + ")");
        startMeshBroadcast();
    }

    public void setHandoffListener(MeshHandoffListener listener) {
        this.handoffListener = listener;
    }

    public String getDeviceName() {
        return myDeviceName;
    }

    public void setDeviceName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.myDeviceName = name.trim();
            prefs.edit().putString(KEY_DEVICE_CUSTOM_NAME, this.myDeviceName).apply();
        }
    }

    public void updatePlaybackState(String channelName, String url, long ptsMs) {
        this.currentChannelName = channelName != null ? channelName : "";
        this.currentChannelUrl = url != null ? url : "";
        this.currentPts = ptsMs;
    }

    public synchronized void startMeshBroadcast() {
        if (isRunning) return;
        isRunning = true;

        new Thread(() -> {
            try {
                socket = new DatagramSocket(MESH_UDP_PORT);
                socket.setBroadcast(true);

                // Start receiver thread
                new Thread(this::receiveLoop).start();

                // Start periodic beacon thread
                new Thread(this::beaconLoop).start();
            } catch (Exception e) {
                Log.w(TAG, "Failed to start Mesh UDP socket on port " + MESH_UDP_PORT, e);
            }
        }).start();
    }

    private void beaconLoop() {
        while (isRunning) {
            try {
                cleanExpiredPeers();
                sendBeacon();
                Thread.sleep(6000);
            } catch (InterruptedException e) {
                break;
            } catch (Exception ignored) {}
        }
    }

    private void sendBeacon() {
        if (socket == null || socket.isClosed()) return;
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "beacon");
            msg.put("deviceId", myDeviceId);
            msg.put("name", myDeviceName);
            msg.put("ip", WebPushServer.getLocalIpAddress());
            msg.put("port", 8888);
            msg.put("channel", currentChannelName);
            msg.put("url", currentChannelUrl);
            msg.put("pts", currentPts);

            byte[] data = msg.toString().getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length,
                    InetAddress.getByName("255.255.255.255"), MESH_UDP_PORT);
            socket.send(packet);
        } catch (Exception ignored) {}
    }

    private void receiveLoop() {
        byte[] buffer = new byte[2048];
        while (isRunning && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String text = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                JSONObject obj = new JSONObject(text);
                String senderId = obj.optString("deviceId", "");

                if (senderId.equals(myDeviceId)) {
                    continue; // Skip self
                }

                String type = obj.optString("type", "");
                if ("beacon".equals(type)) {
                    PeerDevice peer = activePeers.get(senderId);
                    if (peer == null) {
                        peer = new PeerDevice(senderId, obj.optString("name", "局域网电视"), obj.optString("ip", packet.getAddress().getHostAddress()), obj.optInt("port", 8888));
                        activePeers.put(senderId, peer);
                    }
                    peer.currentChannel = obj.optString("channel", "");
                    peer.currentUrl = obj.optString("url", "");
                    peer.currentPtsMs = obj.optLong("pts", 0);
                    peer.lastSeen = System.currentTimeMillis();
                } else if ("handoff".equals(type)) {
                    String targetId = obj.optString("targetDeviceId", "");
                    if (targetId.equals(myDeviceId) && handoffListener != null) {
                        String chName = obj.optString("channel", "");
                        String chUrl = obj.optString("url", "");
                        long pts = obj.optLong("pts", 0);
                        handler.post(() -> handoffListener.onHandoffReceived(chName, chUrl, pts));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public void sendHandoffToPeer(String targetDeviceId) {
        new Thread(() -> {
            if (socket == null || socket.isClosed()) return;
            try {
                JSONObject msg = new JSONObject();
                msg.put("type", "handoff");
                msg.put("sourceDeviceId", myDeviceId);
                msg.put("targetDeviceId", targetDeviceId);
                msg.put("channel", currentChannelName);
                msg.put("url", currentChannelUrl);
                msg.put("pts", currentPts);

                byte[] data = msg.toString().getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(data, data.length,
                        InetAddress.getByName("255.255.255.255"), MESH_UDP_PORT);
                socket.send(packet);
            } catch (Exception ignored) {}
        }).start();
    }

    private void cleanExpiredPeers() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PeerDevice>> it = activePeers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PeerDevice> entry = it.next();
            if (now - entry.getValue().lastSeen > 20000) {
                it.remove();
            }
        }
    }

    public List<PeerDevice> getActivePeers() {
        cleanExpiredPeers();
        return new ArrayList<>(activePeers.values());
    }

    public JSONArray getPeersJson() {
        cleanExpiredPeers();
        JSONArray arr = new JSONArray();
        for (PeerDevice p : activePeers.values()) {
            arr.put(p.toJson());
        }
        return arr;
    }

    public JSONObject getMeshStatusJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("myDeviceId", myDeviceId);
            json.put("myDeviceName", myDeviceName);
            json.put("peerCount", getActivePeers().size());
            json.put("peers", getPeersJson());
            return json;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public void stop() {
        isRunning = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
            socket = null;
        }
    }
}
