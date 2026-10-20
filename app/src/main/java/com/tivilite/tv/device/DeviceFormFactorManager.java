package com.tivilite.tv.device;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * TiviLite Device Form Factor & Responsive Multi-Terminal Manager
 * Accurately detects Android TV, Mobile Phone, Tablet, and Foldable screen profiles.
 */
public class DeviceFormFactorManager {
    public enum FormFactor {
        TV,
        PHONE,
        TABLET,
        FOLDABLE
    }

    private static volatile DeviceFormFactorManager instance;
    private final Context context;
    private final FormFactor formFactor;
    private final boolean hasTouchscreen;
    private final int smallestScreenWidthDp;

    public static DeviceFormFactorManager getInstance(Context context) {
        if (instance == null) {
            synchronized (DeviceFormFactorManager.class) {
                if (instance == null) {
                    instance = new DeviceFormFactorManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private DeviceFormFactorManager(Context context) {
        this.context = context;

        // 1. Detect Touchscreen capability
        PackageManager pm = context.getPackageManager();
        this.hasTouchscreen = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);

        // 2. Measure screen metrics
        Configuration config = context.getResources().getConfiguration();
        this.smallestScreenWidthDp = config.smallestScreenWidthDp;

        // 3. Detect TV leanback or TV UI mode
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        boolean isTvUi = uiModeManager != null && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        boolean hasLeanbackFeature = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        boolean lacksTouch = !hasTouchscreen;

        if (isTvUi || hasLeanbackFeature || lacksTouch) {
            this.formFactor = FormFactor.TV;
        } else if (smallestScreenWidthDp >= 600) {
            this.formFactor = FormFactor.TABLET;
        } else if (isFoldableScreen(context)) {
            this.formFactor = FormFactor.FOLDABLE;
        } else {
            this.formFactor = FormFactor.PHONE;
        }
    }

    private boolean isFoldableScreen(Context context) {
        try {
            Configuration config = context.getResources().getConfiguration();
            int screenWidthDp = config.screenWidthDp;
            int screenHeightDp = config.screenHeightDp;
            float ratio = (float) Math.max(screenWidthDp, screenHeightDp) / Math.min(screenWidthDp, screenHeightDp);
            // Square-ish aspect ratio typically indicates an unfolded foldable device
            return ratio < 1.35f && smallestScreenWidthDp >= 500;
        } catch (Exception e) {
            return false;
        }
    }

    public FormFactor getFormFactor() {
        return formFactor;
    }

    public boolean isTv() {
        return formFactor == FormFactor.TV;
    }

    public boolean isTablet() {
        return formFactor == FormFactor.TABLET;
    }

    public boolean isPhone() {
        return formFactor == FormFactor.PHONE;
    }

    public boolean isFoldable() {
        return formFactor == FormFactor.FOLDABLE;
    }

    public boolean hasTouchscreen() {
        return hasTouchscreen;
    }

    public int getSmallestScreenWidthDp() {
        return smallestScreenWidthDp;
    }

    public boolean shouldEnableTouchGestures() {
        return hasTouchscreen;
    }

    public String getFormFactorName() {
        switch (formFactor) {
            case TV:
                return "📺 智能电视 (Android TV)";
            case TABLET:
                return "📟 平板电脑 (Tablet)";
            case FOLDABLE:
                return "📱 折叠屏设备 (Foldable)";
            case PHONE:
            default:
                return "📱 智能手机 (Phone)";
        }
    }

    public String getDeviceModelInfo() {
        return Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT + ")";
    }
}
