package com.tivilite.tv.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.view.View;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;

public class PictureQualityManager {

    public static final int MODE_STANDARD = 0; // 标准原画
    public static final int MODE_CINEMA = 1;   // 影院胶片 (温润色温，暗部细节提升)
    public static final int MODE_VIVID = 2;    // 鲜艳通透 (高饱和与高动态对比)
    public static final int MODE_SPORTS = 3;   // 体育赛事 (草地翠绿，动态抗拖尾增强)

    private static PictureQualityManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private int currentMode;

    private PictureQualityManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences("tivilite_picture_prefs", Context.MODE_PRIVATE);
        this.currentMode = prefs.getInt("picture_mode", MODE_STANDARD);
    }

    public static synchronized PictureQualityManager getInstance(Context context) {
        if (instance == null) {
            instance = new PictureQualityManager(context);
        }
        return instance;
    }

    public int getCurrentMode() {
        return currentMode;
    }

    public void setMode(int mode, View targetView) {
        this.currentMode = mode;
        prefs.edit().putInt("picture_mode", mode).apply();
        if (targetView != null) {
            applyModeToView(targetView);
        }
    }

    public int cycleNextMode(View targetView) {
        int next = (currentMode + 1) % 4;
        setMode(next, targetView);
        return next;
    }

    public String getModeName() {
        return getModeName(currentMode);
    }

    public String getModeName(int mode) {
        switch (mode) {
            case MODE_CINEMA:
                return "影院胶片 (Cinema)";
            case MODE_VIVID:
                return "鲜艳通透 (Vivid)";
            case MODE_SPORTS:
                return "体育赛事 (Sports)";
            case MODE_STANDARD:
            default:
                return "标准原画 (Standard)";
        }
    }

    public void applyModeToView(View targetView) {
        if (targetView == null) return;

        if (currentMode == MODE_STANDARD) {
            targetView.setLayerType(View.LAYER_TYPE_NONE, null);
            return;
        }

        ColorMatrix cm = new ColorMatrix();
        switch (currentMode) {
            case MODE_CINEMA: {
                // 胶片温和调色：饱和度略收 0.96，对比微升 1.06，暗部提亮，微暖色调
                cm.setSaturation(0.96f);
                float contrast = 1.06f;
                float brightness = 4f;
                ColorMatrix cmContrast = new ColorMatrix(new float[]{
                        contrast, 0, 0, 0, brightness + 3f, // 暖红微升
                        0, contrast, 0, 0, brightness + 1f, // 绿微升
                        0, 0, contrast, 0, brightness - 4f, // 蓝微降
                        0, 0, 0, 1, 0
                });
                cm.postConcat(cmContrast);
                break;
            }
            case MODE_VIVID: {
                // 鲜艳通透：高饱和度 1.25，对比度 1.18，通透明亮
                cm.setSaturation(1.25f);
                float contrast = 1.16f;
                float brightness = 6f;
                ColorMatrix cmContrast = new ColorMatrix(new float[]{
                        contrast, 0, 0, 0, brightness,
                        0, contrast, 0, 0, brightness,
                        0, 0, contrast, 0, brightness,
                        0, 0, 0, 1, 0
                });
                cm.postConcat(cmContrast);
                break;
            }
            case MODE_SPORTS: {
                // 体育赛事：强化绿茵场翠绿与足球对比度，饱和度 1.18，对比度 1.12
                cm.setSaturation(1.18f);
                float contrast = 1.12f;
                float brightness = 5f;
                ColorMatrix cmContrast = new ColorMatrix(new float[]{
                        contrast, 0, 0, 0, brightness,
                        0, contrast * 1.05f, 0, 0, brightness + 5f, // 强化草地绿意
                        0, 0, contrast, 0, brightness,
                        0, 0, 0, 1, 0
                });
                cm.postConcat(cmContrast);
                break;
            }
        }

        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        targetView.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
    }

    /**
     * 智能识别视频流 HDR 规格 (HDR10 / HLG / 杜比视界) 与分辨率
     */
    public String getHdrAndResolutionBadge(Format format) {
        if (format == null) return "";
        StringBuilder sb = new StringBuilder();

        // 1. 分辨率
        int width = format.width;
        int height = format.height;
        if (width >= 3800 || height >= 2100) {
            sb.append("4K UHD");
        } else if (width >= 1900 || height >= 1000) {
            sb.append("1080P FHD");
        } else if (width > 0 && height > 0) {
            sb.append(height).append("P");
        }

        // 2. 帧率
        float fps = format.frameRate;
        if (fps > 45) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(Math.round(fps)).append("FPS");
        }

        // 3. HDR / 色彩空间检测
        ColorInfo colorInfo = format.colorInfo;
        if (colorInfo != null) {
            int transfer = colorInfo.colorTransfer;
            if (transfer == C.COLOR_TRANSFER_ST2084) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append("HDR10");
            } else if (transfer == C.COLOR_TRANSFER_HLG) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append("HLG HDR");
            } else if (colorInfo.colorSpace == C.COLOR_SPACE_BT2020) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append("BT.2020 广色域");
            }
        }

        return sb.toString();
    }
}
