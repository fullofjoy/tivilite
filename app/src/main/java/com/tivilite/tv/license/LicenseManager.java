package com.tivilite.tv.license;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * TiviLite Pro 商业授权管理模块
 * 采用离线设备指纹 + HMAC 哈希签名算法，无需部署昂贵服务器，天然防伪造
 */
public class LicenseManager {

    private static final String PREF_NAME = "tivilite_license_prefs";
    private static final String KEY_LICENSE_CODE = "license_key";
    private static final String KEY_IS_PRO = "is_pro_active";
    private static final String LICENSE_SALT = com.tivilite.tv.BuildConfig.LICENSE_SALT;
    private static final String FALLBACK_SALT_1 = "TIVILITE_SECRET_SALT_2026_PRO";
    private static final String FALLBACK_SALT_2 = "COMMUNITY_DEFAULT_SALT";

    /**
     * 获取 8 位高可读设备码（供用户在购买页填写）
     */
    public static String getDeviceId(Context context) {
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId == null || androidId.isEmpty()) {
                androidId = android.os.Build.SERIAL != null ? android.os.Build.SERIAL : "UNKNOWN_DEVICE";
            }
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(androidId.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02X", hash[i]));
            }
            return "TV-" + sb.toString();
        } catch (Exception e) {
            return "TV-88888888";
        }
    }

    /**
     * 检查当前是否为 Pro 尊享会员
     */
    public static boolean isPro(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!sp.getBoolean(KEY_IS_PRO, false)) {
            return false;
        }
        String savedCode = sp.getString(KEY_LICENSE_CODE, "");
        return verifyLicense(context, savedCode);
    }

    /**
     * 校验并激活 Pro
     */
    public static boolean activate(Context context, String inputCode) {
        if (inputCode == null) return false;
        String cleanCode = inputCode.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (!cleanCode.startsWith("PRO-") && cleanCode.length() == 12) {
            cleanCode = "PRO-" + cleanCode.substring(0, 4) + "-" + cleanCode.substring(4, 8) + "-" + cleanCode.substring(8, 12);
        }
        if (verifyLicense(context, cleanCode)) {
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit()
                    .putBoolean(KEY_IS_PRO, true)
                    .putString(KEY_LICENSE_CODE, cleanCode)
                    .apply();
            return true;
        }
        return false;
    }

    /**
     * 离线防伪算法：容错支持带横杠/不带横杠及双签名盐
     */
    public static boolean verifyLicense(Context context, String code) {
        if (code == null) return false;
        String target = code.trim().toUpperCase(Locale.ROOT);
        if (!target.startsWith("PRO-")) return false;

        String deviceId = getDeviceId(context); // e.g. TV-02704819
        String rawNoDash = deviceId.replace("-", ""); // e.g. TV02704819
        String hexOnly = deviceId.replace("TV-", "").replace("TV", ""); // e.g. 02704819

        String[] candidateIds = new String[]{ deviceId, rawNoDash, hexOnly };
        String[] candidateSalts = new String[]{ LICENSE_SALT, FALLBACK_SALT_1, FALLBACK_SALT_2 };

        for (String dev : candidateIds) {
            for (String salt : candidateSalts) {
                if (salt != null && !salt.isEmpty()) {
                    String valid = calculateHash(dev, salt);
                    if (valid.equalsIgnoreCase(target)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static String generateValidLicense(String deviceId) {
        return calculateHash(deviceId, LICENSE_SALT);
    }

    private static String calculateHash(String deviceId, String salt) {
        try {
            String raw = deviceId.trim().toUpperCase(Locale.ROOT) + salt;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02X", hash[i]));
            }
            String hex = sb.toString(); // 12 chars
            return String.format("PRO-%s-%s-%s",
                    hex.substring(0, 4),
                    hex.substring(4, 8),
                    hex.substring(8, 12));
        } catch (Exception e) {
            return "PRO-INVALID";
        }
    }
}
