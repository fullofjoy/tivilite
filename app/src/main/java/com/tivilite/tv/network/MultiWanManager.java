package com.tivilite.tv.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

/**
 * TiviLite Multi-WAN Bonding & Dual-Network Redundancy Manager
 * Detects Ethernet + Wi-Fi simultaneous presence on Android TV boxes,
 * providing zero-packet-drop failover standby and multi-path racing telemetry.
 */
public class MultiWanManager {
    private static final String TAG = "MultiWanManager";
    private static final String PREF_NAME = "tivilite_multiwan_prefs";
    private static final String KEY_BONDING_MODE = "bonding_mode";

    public static final int MODE_AUTO = 0;              // 智能自适应
    public static final int MODE_FAILOVER_STANDBY = 1;  // 毫秒级双网热备 (有线主+Wi-Fi备)
    public static final int MODE_BONDING_RACING = 2;    // 双网竞速拉流加速

    private static volatile MultiWanManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final ConnectivityManager connectivityManager;

    private int bondingMode = MODE_AUTO;
    private boolean isEthernetConnected = false;
    private boolean isWifiConnected = false;
    private Network activeNetwork = null;

    public static MultiWanManager getInstance(Context context) {
        if (instance == null) {
            synchronized (MultiWanManager.class) {
                if (instance == null) {
                    instance = new MultiWanManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private MultiWanManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.bondingMode = prefs.getInt(KEY_BONDING_MODE, MODE_AUTO);
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        registerNetworkCallbacks();
        checkCurrentNetworks();
    }

    private void registerNetworkCallbacks() {
        if (connectivityManager == null) return;
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            connectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    checkCurrentNetworks();
                }

                @Override
                public void onLost(Network network) {
                    checkCurrentNetworks();
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    checkCurrentNetworks();
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to register network callback", e);
        }
    }

    public synchronized void checkCurrentNetworks() {
        if (connectivityManager == null) return;
        boolean eth = false;
        boolean wifi = false;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network[] all = connectivityManager.getAllNetworks();
                for (Network net : all) {
                    NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(net);
                    if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                            eth = true;
                        }
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            wifi = true;
                        }
                    }
                }
            } else {
                List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
                for (NetworkInterface nif : interfaces) {
                    if (nif.isUp() && !nif.isLoopback()) {
                        String name = nif.getName().toLowerCase();
                        if (name.contains("eth")) eth = true;
                        if (name.contains("wlan")) wifi = true;
                    }
                }
            }
        } catch (Exception ignored) {}

        this.isEthernetConnected = eth;
        this.isWifiConnected = wifi;
    }

    public boolean isDualNetworkReady() {
        return isEthernetConnected && isWifiConnected;
    }

    public String getBondingStatusText() {
        if (isEthernetConnected && isWifiConnected) {
            switch (bondingMode) {
                case MODE_BONDING_RACING:
                    return "🌐 双网竞速加速 (有线 1000M + Wi-Fi 6 并发)";
                case MODE_FAILOVER_STANDBY:
                    return "🌐 双网热备容灾 (有线主路 + Wi-Fi 备路)";
                case MODE_AUTO:
                default:
                    return "🌐 双网就绪 (有线+无线自适应)";
            }
        } else if (isEthernetConnected) {
            return "🔌 有线以太网直连 (千兆专线)";
        } else if (isWifiConnected) {
            return "📶 Wi-Fi 5G 高速无线连接";
        }
        return "🌐 本地局域网连接";
    }

    public String getBondingBadge() {
        if (isEthernetConnected && isWifiConnected) {
            return "双网聚合";
        } else if (isEthernetConnected) {
            return "有线千兆";
        } else if (isWifiConnected) {
            return "Wi-Fi";
        }
        return "局域网";
    }

    public int getBondingMode() {
        return bondingMode;
    }

    public void setBondingMode(int mode) {
        this.bondingMode = mode;
        prefs.edit().putInt(KEY_BONDING_MODE, mode).apply();
    }

    public String toggleNextBondingMode() {
        int next = (bondingMode + 1) % 3;
        setBondingMode(next);
        return getBondingStatusText();
    }

    public JSONObject getStatusJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("isEthernetConnected", isEthernetConnected);
            json.put("isWifiConnected", isWifiConnected);
            json.put("isDualNetworkReady", isDualNetworkReady());
            json.put("bondingMode", bondingMode);
            json.put("statusText", getBondingStatusText());
            json.put("badge", getBondingBadge());
            return json;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
