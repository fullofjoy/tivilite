package com.tivilite.tv.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.tivilite.tv.data.SubscriptionManager;
import com.tivilite.tv.favorite.FavoriteManager;
import com.tivilite.tv.model.Channel;
import com.tivilite.tv.server.WebPushServer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * TiviLite Zero-Config LAN Subscription Mirroring & Clone Engine
 * Automatically broadcasts, discovers and mirrors subscriptions and favorites
 * across TVs, phones, tablets and PCs on the same Wi-Fi with zero manual typing.
 */
public class LanSubscriptionMirrorManager {
    private static final String TAG = "LanSubMirror";
    private static final int MIRROR_PORT = 9877;
    private static final String PREF_NAME = "tivilite_mirror_prefs";
    private static final String KEY_AUTO_CLONE = "auto_clone_on_first_launch";
    private static final String KEY_ALLOW_SHARE = "allow_share_subscriptions";

    private static volatile LanSubscriptionMirrorManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isListening = false;
    private DatagramSocket socket;

    public interface MirrorCallback {
        void onMirrored(String sourceDeviceName, int subscriptionCount, int channelCount);
        void onError(String message);
    }

    public static LanSubscriptionMirrorManager getInstance(Context context) {
        if (instance == null) {
            synchronized (LanSubscriptionMirrorManager.class) {
                if (instance == null) {
                    instance = new LanSubscriptionMirrorManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private LanSubscriptionMirrorManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        startListeningServer();
    }

    public boolean isAutoCloneEnabled() {
        return prefs.getBoolean(KEY_AUTO_CLONE, true);
    }

    public void setAutoCloneEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_CLONE, enabled).apply();
    }

    public boolean isAllowShareEnabled() {
        return prefs.getBoolean(KEY_ALLOW_SHARE, true);
    }

    public boolean toggleAllowShare() {
        boolean current = isAllowShareEnabled();
        prefs.edit().putBoolean(KEY_ALLOW_SHARE, !current).apply();
        return !current;
    }

    /**
     * Background UDP Server that listens for MIRROR_REQUEST and responds with subscriptions.
     */
    private void startListeningServer() {
        if (isListening) return;
        isListening = true;

        new Thread(() -> {
            try {
                socket = new DatagramSocket(MIRROR_PORT);
                socket.setBroadcast(true);
                byte[] buffer = new byte[8192];

                while (isListening && !socket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    InetAddress senderAddr = packet.getAddress();
                    int senderPort = packet.getPort();

                    handleIncomingPacket(message, senderAddr, senderPort);
                }
            } catch (Exception e) {
                Log.w(TAG, "Mirror socket stopped or port busy: " + e.getMessage());
            }
        }).start();
    }

    private void handleIncomingPacket(String message, InetAddress senderAddr, int senderPort) {
        try {
            JSONObject json = new JSONObject(message);
            String action = json.optString("action");

            if ("SUB_SYNC_REQUEST".equals(action)) {
                if (!isAllowShareEnabled()) return;

                // Build subscription bundle
                List<SubscriptionManager.Subscription> subs = SubscriptionManager.getAllSubscriptions(context);
                if (subs.isEmpty()) return;

                JSONObject response = new JSONObject();
                response.put("action", "SUB_SYNC_RESPONSE");
                response.put("deviceName", android.os.Build.MODEL + " (TiviLite Master)");

                JSONArray subArr = new JSONArray();
                for (SubscriptionManager.Subscription s : subs) {
                    if (s.isEnabled()) {
                        JSONObject sObj = new JSONObject();
                        sObj.put("id", s.getId());
                        sObj.put("name", s.getName());
                        sObj.put("url", s.getUrl());
                        sObj.put("content", s.getContent());
                        subArr.put(sObj);
                    }
                }
                response.put("subscriptions", subArr);

                // Add favorites
                Set<String> favs = FavoriteManager.getFavoriteNames(context);
                JSONArray favArr = new JSONArray();
                for (String f : favs) favArr.put(f);
                response.put("favorites", favArr);

                byte[] replyData = response.toString().getBytes(StandardCharsets.UTF_8);
                DatagramPacket replyPacket = new DatagramPacket(replyData, replyData.length, senderAddr, senderPort);
                socket.send(replyPacket);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling incoming mirror packet: " + e.getMessage());
        }
    }

    /**
     * Actively broadcasts a request to clone subscriptions from any master device on the LAN.
     */
    public void requestCloneFromLan(MirrorCallback callback) {
        new Thread(() -> {
            DatagramSocket clientSocket = null;
            try {
                clientSocket = new DatagramSocket();
                clientSocket.setBroadcast(true);
                clientSocket.setSoTimeout(3500);

                JSONObject req = new JSONObject();
                req.put("action", "SUB_SYNC_REQUEST");
                req.put("clientModel", android.os.Build.MODEL);

                byte[] data = req.toString().getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), MIRROR_PORT);
                clientSocket.send(packet);

                byte[] receiveBuf = new byte[32768];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
                clientSocket.receive(receivePacket);

                String replyStr = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
                JSONObject replyJson = new JSONObject(replyStr);

                if ("SUB_SYNC_RESPONSE".equals(replyJson.optString("action"))) {
                    String devName = replyJson.optString("deviceName", "局域网主机");
                    JSONArray subArr = replyJson.optJSONArray("subscriptions");
                    int importedCount = 0;
                    int channelTotal = 0;

                    if (subArr != null) {
                        for (int i = 0; i < subArr.length(); i++) {
                            JSONObject sObj = subArr.getJSONObject(i);
                            String name = sObj.optString("name", "克隆订阅");
                            String url = sObj.optString("url", "");
                            String content = sObj.optString("content", "");

                            if (!content.isEmpty() || !url.isEmpty()) {
                                List<Channel> parsed = com.tivilite.tv.parser.M3UParser.parse(content);
                                SubscriptionManager.Subscription sub = new SubscriptionManager.Subscription(
                                        java.util.UUID.randomUUID().toString(),
                                        name + " (局域网镜像)",
                                        url,
                                        content,
                                        parsed.size(),
                                        true,
                                        System.currentTimeMillis()
                                );
                                SubscriptionManager.addOrUpdateSubscription(context, sub, false);
                                importedCount++;
                                channelTotal += parsed.size();
                            }
                        }
                    }

                    // Restore favorites
                    JSONArray favArr = replyJson.optJSONArray("favorites");
                    if (favArr != null) {
                        java.util.Set<String> curFavs = new java.util.HashSet<>(FavoriteManager.getFavoriteNames(context));
                        for (int i = 0; i < favArr.length(); i++) {
                            curFavs.add(favArr.getString(i));
                        }
                        FavoriteManager.setFavoriteNames(context, curFavs);
                    }

                    final int finalSubs = importedCount;
                    final int finalChs = channelTotal;
                    handler.post(() -> {
                        if (callback != null) callback.onMirrored(devName, finalSubs, finalChs);
                    });
                }
            } catch (Exception e) {
                handler.post(() -> {
                    if (callback != null) callback.onError("未在局域网内探测到已配源的主机 (超时或未开启共享)");
                });
            } finally {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            }
        }).start();
    }

    public JSONObject getMirrorStatusJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("autoCloneEnabled", isAutoCloneEnabled());
            json.put("allowShareEnabled", isAllowShareEnabled());
            json.put("port", MIRROR_PORT);
            json.put("subscriptionsCount", SubscriptionManager.getAllSubscriptions(context).size());
        } catch (Exception ignored) {}
        return json;
    }
}
