package com.tivilite.tv.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import org.json.JSONObject;

/**
 * TiviLite Large-Screen Visual Theme & Font Scale Customizer
 * Supports:
 * 1. Cyber Dark (Default Obsidian + Neon Cyan/Electric Blue)
 * 2. Glassmorphism (Frosted Glass Acrylic Translucent)
 * 3. Warm Cinema (Deep Charcoal + Amber Champagne)
 * 4. Senior High Contrast (Pure OLED Black + Bright Yellow Extra-Large Text)
 */
public class ThemeManager {
    private static final String TAG = "ThemeManager";
    private static final String PREF_NAME = "tivilite_theme_prefs";
    private static final String KEY_THEME_ID = "theme_id";
    private static final String KEY_FONT_SCALE = "font_scale";

    public static final int THEME_CYBER_DARK = 0;
    public static final int THEME_GLASSMORPHISM = 1;
    public static final int THEME_WARM_CINEMA = 2;
    public static final int THEME_SENIOR_HIGH_CONTRAST = 3;

    public static final int FONT_NORMAL = 0;
    public static final int FONT_COMPACT = 1;
    public static final int FONT_LARGE = 2;
    public static final int FONT_SENIOR = 3;

    private static volatile ThemeManager instance;
    private final Context context;
    private final SharedPreferences prefs;

    private int currentTheme = THEME_CYBER_DARK;
    private int currentFontScale = FONT_NORMAL;

    public static ThemeManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ThemeManager.class) {
                if (instance == null) {
                    instance = new ThemeManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private ThemeManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.currentTheme = prefs.getInt(KEY_THEME_ID, THEME_CYBER_DARK);
        this.currentFontScale = prefs.getInt(KEY_FONT_SCALE, FONT_NORMAL);
    }

    public int getCurrentTheme() {
        return currentTheme;
    }

    public void setTheme(int themeId) {
        this.currentTheme = themeId;
        prefs.edit().putInt(KEY_THEME_ID, themeId).apply();
    }

    public String toggleNextTheme() {
        int next = (currentTheme + 1) % 4;
        setTheme(next);
        return getThemeName();
    }

    public String getThemeName() {
        switch (currentTheme) {
            case THEME_GLASSMORPHISM:
                return "极致毛玻璃 (通透冷灰)";
            case THEME_WARM_CINEMA:
                return "温暖影院 (琥珀金灰)";
            case THEME_SENIOR_HIGH_CONTRAST:
                return "长辈关爱 (纯黑高对比大字)";
            case THEME_CYBER_DARK:
            default:
                return "赛博暗黑 (极客蓝青)";
        }
    }

    public int getCurrentFontScale() {
        return currentFontScale;
    }

    public void setFontScale(int scale) {
        this.currentFontScale = scale;
        prefs.edit().putInt(KEY_FONT_SCALE, scale).apply();
    }

    public String toggleNextFontScale() {
        int next = (currentFontScale + 1) % 4;
        setFontScale(next);
        return getFontScaleName();
    }

    public String getFontScaleName() {
        switch (currentFontScale) {
            case FONT_COMPACT:
                return "紧凑视野 (小字号)";
            case FONT_LARGE:
                return "大屏舒适 (中大字号)";
            case FONT_SENIOR:
                return "长辈特大 (1.5x 特大字号)";
            case FONT_NORMAL:
            default:
                return "标准字号 (1.0x)";
        }
    }

    public float getFontMultiplier() {
        switch (currentFontScale) {
            case FONT_COMPACT:
                return 0.88f;
            case FONT_LARGE:
                return 1.25f;
            case FONT_SENIOR:
                return 1.55f;
            case FONT_NORMAL:
            default:
                return 1.0f;
        }
    }

    public int getAccentColor() {
        switch (currentTheme) {
            case THEME_GLASSMORPHISM:
                return Color.parseColor("#E0E7FF"); // Soft ice blue
            case THEME_WARM_CINEMA:
                return Color.parseColor("#F59E0B"); // Warm Amber
            case THEME_SENIOR_HIGH_CONTRAST:
                return Color.parseColor("#FACC15"); // Bright High-Contrast Yellow
            case THEME_CYBER_DARK:
            default:
                return Color.parseColor("#00E5FF"); // Cyan
        }
    }

    public int getCardBackgroundColor() {
        switch (currentTheme) {
            case THEME_GLASSMORPHISM:
                return Color.parseColor("#C8182236"); // Semi-translucent frosted glass
            case THEME_WARM_CINEMA:
                return Color.parseColor("#F2181512"); // Warm charcoal
            case THEME_SENIOR_HIGH_CONTRAST:
                return Color.parseColor("#FF000000"); // Solid pure black
            case THEME_CYBER_DARK:
            default:
                return Color.parseColor("#EA0F131D"); // Deep obsidian
        }
    }

    public void applyThemeToCard(CardView card) {
        if (card != null) {
            card.setCardBackgroundColor(getCardBackgroundColor());
        }
    }

    public void applyThemeToView(View view) {
        if (view instanceof CardView) {
            applyThemeToCard((CardView) view);
        } else if (view != null) {
            view.setBackgroundColor(getCardBackgroundColor());
        }
    }

    public JSONObject getThemeStatusJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("themeId", currentTheme);
            json.put("themeName", getThemeName());
            json.put("fontScale", currentFontScale);
            json.put("fontScaleName", getFontScaleName());
            return json;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
