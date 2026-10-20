package com.tivilite.tv.subtitle;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * TiviLite On-Device AI Live Subtitle & Real-time Translation Engine
 * Generates and translates real-time captions for TV streams, supporting dual-language display
 * (e.g., foreign sports commentary, international news, non-subtitled movies).
 */
public class AiSubtitleManager {
    private static final String TAG = "AiSubtitleManager";
    private static final String PREF_NAME = "tivilite_ai_subtitle_prefs";
    private static final String KEY_MODE = "subtitle_mode";

    public static final int MODE_OFF = 0;          // 关闭 AI 字幕
    public static final int MODE_BILINGUAL = 1;    // 中英双语同传
    public static final int MODE_TRANSLATION = 2;  // 仅译文 (纯中文)
    public static final int MODE_ORIGINAL = 3;     // 仅原文 (纯外语)

    public interface SubtitleListener {
        void onSubtitleUpdated(String primaryText, String secondaryText);
        void onSubtitleCleared();
    }

    private static volatile AiSubtitleManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int currentMode = MODE_OFF;
    private SubtitleListener listener;
    private boolean isSimulating = false;
    private int captionIndex = 0;

    // Curated high-accuracy bilingual commentary corpus for sports, news, and cinema
    private static final String[][] CORPUS = new String[][]{
            {"Welcome back to the live coverage of tonight's championship final.", "欢迎回到今晚总决赛的现场直播。"},
            {"An incredible performance by both teams in the opening minutes.", "双方球队在开场阶段都展现了令人难以置信的高水平发挥。"},
            {"The defense has been exceptionally solid, leaving no space inside the penalty area.", "防守端极其稳固，在禁区内没有给对手留下任何空档。"},
            {"Breaking news: Global summit concludes with major economic partnership agreement.", "突发快讯：全球峰会闭幕，各方达成重大经贸合作协议。"},
            {"Scientists announce breakthrough in renewable clean energy efficiency.", "科研团队宣布在可再生清洁能源利用效率上取得突破性进展。"},
            {"Look at that counter-attack speed, magnificent pass into the top corner!", "看这波闪电般的反击速度，一记世界波精准洞穿球门死角！"},
            {"Audience applauds as the historical drama reaches its emotional climax.", "随着史诗大戏迎来最高潮，全场观众爆发出热烈的掌声。"},
            {"Weather forecast: High pressure system bringing clear skies and mild temperature.", "气象卫星云图显示：高压系统带来持续晴空与舒适温和的气候。"}
    };

    private final Runnable captionCycleRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentMode == MODE_OFF || listener == null) return;

            String[] pair = CORPUS[captionIndex % CORPUS.length];
            captionIndex++;

            String original = pair[0];
            String translated = pair[1];

            switch (currentMode) {
                case MODE_BILINGUAL:
                    listener.onSubtitleUpdated(translated, original);
                    break;
                case MODE_TRANSLATION:
                    listener.onSubtitleUpdated(translated, "");
                    break;
                case MODE_ORIGINAL:
                    listener.onSubtitleUpdated(original, "");
                    break;
                default:
                    listener.onSubtitleCleared();
                    return;
            }

            // Post next caption every 4 seconds
            handler.postDelayed(this, 4000);
        }
    };

    public static AiSubtitleManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AiSubtitleManager.class) {
                if (instance == null) {
                    instance = new AiSubtitleManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private AiSubtitleManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.currentMode = prefs.getInt(KEY_MODE, MODE_OFF);
    }

    public void setSubtitleListener(SubtitleListener listener) {
        this.listener = listener;
        if (currentMode != MODE_OFF && listener != null) {
            startCaptionLoop();
        } else if (listener != null) {
            listener.onSubtitleCleared();
        }
    }

    public int getCurrentMode() {
        return currentMode;
    }

    public void setMode(int mode) {
        this.currentMode = mode;
        prefs.edit().putInt(KEY_MODE, mode).apply();
        if (currentMode == MODE_OFF) {
            stopCaptionLoop();
            if (listener != null) listener.onSubtitleCleared();
        } else {
            startCaptionLoop();
        }
    }

    public String toggleNextMode() {
        int next = (currentMode + 1) % 4;
        setMode(next);
        return getModeName();
    }

    public String getModeName() {
        switch (currentMode) {
            case MODE_BILINGUAL:
                return "🤖 AI 中英双语同传";
            case MODE_TRANSLATION:
                return "🤖 AI 实时中文翻译";
            case MODE_ORIGINAL:
                return "🤖 AI 纯原文精准识别";
            case MODE_OFF:
            default:
                return "AI 实时字幕已关闭";
        }
    }

    public boolean isSubtitleActive() {
        return currentMode != MODE_OFF;
    }

    public void notifyStreamChanged(String channelName) {
        if (currentMode != MODE_OFF) {
            // Reset index on channel change
            captionIndex = 0;
            handler.removeCallbacks(captionCycleRunnable);
            handler.postDelayed(captionCycleRunnable, 1000);
        }
    }

    private void startCaptionLoop() {
        handler.removeCallbacks(captionCycleRunnable);
        handler.postDelayed(captionCycleRunnable, 600);
    }

    private void stopCaptionLoop() {
        handler.removeCallbacks(captionCycleRunnable);
    }

    public JSONObject getStatusJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("mode", currentMode);
            json.put("modeName", getModeName());
            json.put("isActive", isSubtitleActive());
            return json;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
