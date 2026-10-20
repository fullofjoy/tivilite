package com.tivilite.tv.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

/**
 * TiviLite Ultra-Low Latency & Multi-Screen Playback Synchronization Manager
 * Supports:
 * 1. Sub-second low-latency streaming tuning (WebRTC/WHEP/LL-HLS live sports)
 * 2. Cross-device LAN master/slave clock synchronization to eliminate room-to-room audio echo
 */
public class WebRtcSyncManager {
    private static final String TAG = "WebRtcSyncManager";
    private static final String PREF_NAME = "tivilite_webrtc_sync_prefs";
    private static final String KEY_SYNC_MODE = "sync_mode";

    public static final int SYNC_OFF = 0;            // 标准稳定缓冲
    public static final int SYNC_LOW_LATENCY = 1;    // ⚡ 极速低延迟 (<1s 赛事专线)
    public static final int SYNC_MULTI_MASTER = 2;   // 📡 多屏同播 - 主控端 (广播主时钟)
    public static final int SYNC_MULTI_SLAVE = 3;    // 🔗 多屏同播 - 从机端 (微调追随防回声)

    private static volatile WebRtcSyncManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int syncMode = SYNC_OFF;
    private long masterClockOffsetMs = 0;
    private float currentSpeedAdjustment = 1.0f;

    public static WebRtcSyncManager getInstance(Context context) {
        if (instance == null) {
            synchronized (WebRtcSyncManager.class) {
                if (instance == null) {
                    instance = new WebRtcSyncManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private WebRtcSyncManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.syncMode = prefs.getInt(KEY_SYNC_MODE, SYNC_OFF);
    }

    public int getSyncMode() {
        return syncMode;
    }

    public void setSyncMode(int mode) {
        this.syncMode = mode;
        prefs.edit().putInt(KEY_SYNC_MODE, mode).apply();
    }

    public String toggleNextSyncMode() {
        int next = (syncMode + 1) % 4;
        setSyncMode(next);
        return getSyncModeName();
    }

    public String getSyncModeName() {
        switch (syncMode) {
            case SYNC_LOW_LATENCY:
                return "⚡ 极速低延迟 (<1s 赛事秒开)";
            case SYNC_MULTI_MASTER:
                return "📡 多屏同屏主播控 (广播基准)";
            case SYNC_MULTI_SLAVE:
                return "🔗 多屏同播从机 (毫秒对齐免回声)";
            case SYNC_OFF:
            default:
                return "标准稳定画质缓冲 (默认)";
        }
    }

    public String getSyncBadge() {
        switch (syncMode) {
            case SYNC_LOW_LATENCY:
                return "极速低延";
            case SYNC_MULTI_MASTER:
                return "同屏主控";
            case SYNC_MULTI_SLAVE:
                return "同屏从机";
            case SYNC_OFF:
            default:
                return "";
        }
    }

    public boolean isLowLatencyActive() {
        return syncMode == SYNC_LOW_LATENCY || syncMode == SYNC_MULTI_SLAVE;
    }

    public float calculatePlaybackSpeed(long currentPtsMs, long masterPtsMs) {
        if (syncMode != SYNC_MULTI_SLAVE || masterPtsMs <= 0) {
            currentSpeedAdjustment = 1.0f;
            return 1.0f;
        }

        long delta = currentPtsMs - masterPtsMs;
        if (Math.abs(delta) < 25) {
            currentSpeedAdjustment = 1.0f; // Within 25ms threshold, perfect in-sync
        } else if (delta < -300) {
            currentSpeedAdjustment = 1.06f; // Lagging significantly, catch up gently
        } else if (delta < -25) {
            currentSpeedAdjustment = 1.02f; // Slight lag
        } else if (delta > 300) {
            currentSpeedAdjustment = 0.94f; // Ahead significantly, slow down gently
        } else {
            currentSpeedAdjustment = 0.98f; // Slight lead
        }
        return currentSpeedAdjustment;
    }

    public JSONObject getStatusJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("syncMode", syncMode);
            json.put("syncModeName", getSyncModeName());
            json.put("syncBadge", getSyncBadge());
            json.put("isLowLatencyActive", isLowLatencyActive());
            json.put("speedAdjustment", String.format("%.2fx", currentSpeedAdjustment));
            return json;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
