package com.tivilite.tv.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

/**
 * TiviLite Mobile Cellular Data Guard Manager
 * Detects 4G/5G mobile cellular connections, warns users about data consumption,
 * and prevents accidental mobile bandwidth overuse on phones and tablets.
 */
public class CellularDataGuardManager {
    private static final String PREF_NAME = "tivilite_cellular_guard";
    private static final String KEY_GUARD_ENABLED = "cellular_guard_enabled";

    private static volatile CellularDataGuardManager instance;
    private final Context context;
    private final SharedPreferences prefs;

    public static CellularDataGuardManager getInstance(Context context) {
        if (instance == null) {
            synchronized (CellularDataGuardManager.class) {
                if (instance == null) {
                    instance = new CellularDataGuardManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private CellularDataGuardManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isCellularGuardEnabled() {
        return prefs.getBoolean(KEY_GUARD_ENABLED, true);
    }

    public boolean toggleCellularGuard() {
        boolean current = isCellularGuardEnabled();
        prefs.edit().putBoolean(KEY_GUARD_ENABLED, !current).apply();
        return !current;
    }

    public boolean isCellularNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNet = cm.getActiveNetwork();
                if (activeNet == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(activeNet);
                return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            } else {
                android.net.NetworkInfo info = cm.getActiveNetworkInfo();
                return info != null && info.getType() == ConnectivityManager.TYPE_MOBILE;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isWifiOrEthernet() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNet = cm.getActiveNetwork();
                if (activeNet == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(activeNet);
                if (caps == null) return false;
                return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            } else {
                android.net.NetworkInfo info = cm.getActiveNetworkInfo();
                return info != null && (info.getType() == ConnectivityManager.TYPE_WIFI || info.getType() == ConnectivityManager.TYPE_ETHERNET);
            }
        } catch (Exception e) {
            return false;
        }
    }

    public String getNetworkTypeBadge() {
        if (isCellularNetwork()) {
            return "📶 4G/5G 移动蜂窝";
        } else if (isWifiOrEthernet()) {
            return "📡 Wi-Fi/以太网";
        }
        return "🌐 在线";
    }

    public String getCellularGuardStatusText() {
        return isCellularGuardEnabled() ? "📶 移动蜂窝流量防护: 已开启 (防偷跑流量)" : "📶 移动蜂窝流量防护: 已关闭 (允许流量播放)";
    }
}
