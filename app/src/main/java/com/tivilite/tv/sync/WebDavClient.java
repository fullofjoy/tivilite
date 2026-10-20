package com.tivilite.tv.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import com.tivilite.tv.data.SubscriptionManager;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Lightweight, zero-dependency WebDAV client for TiviLite.
 * Enables synchronization of subscriptions, favorites, and settings
 * with private WebDAV servers (Alist, Synology NAS, Nextcloud, Jianguo Yun, etc.).
 */
public class WebDavClient {

    private static final String PREF_KEY_URL = "webdav_url";
    private static final String PREF_KEY_USER = "webdav_user";
    private static final String PREF_KEY_PASS = "webdav_pass";
    private static final String PREF_KEY_AUTO = "webdav_auto_sync";
    private static final String PREF_KEY_LAST_TIME = "webdav_last_sync_time";
    private static final String BACKUP_FILENAME = "tivilite_backup.json";

    public static class SyncResult {
        public final boolean success;
        public final String message;
        public final long timestamp;

        public SyncResult(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static String getWebDavUrl(Context context) {
        return context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .getString(PREF_KEY_URL, "");
    }

    public static String getWebDavUser(Context context) {
        return context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .getString(PREF_KEY_USER, "");
    }

    public static String getWebDavPass(Context context) {
        return context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .getString(PREF_KEY_PASS, "");
    }

    public static boolean isWebDavAutoSync(Context context) {
        return context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .getBoolean(PREF_KEY_AUTO, false);
    }

    public static long getLastSyncTime(Context context) {
        return context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .getLong(PREF_KEY_LAST_TIME, 0);
    }

    public static String getFormattedLastSyncTime(Context context) {
        long t = getLastSyncTime(context);
        if (t <= 0) return "暂未同步";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(t));
    }

    public static void saveWebDavConfig(Context context, String url, String user, String pass, boolean autoSync) {
        context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_URL, url != null ? url.trim() : "")
                .putString(PREF_KEY_USER, user != null ? user.trim() : "")
                .putString(PREF_KEY_PASS, pass != null ? pass.trim() : "")
                .putBoolean(PREF_KEY_AUTO, autoSync)
                .apply();
    }

    public static void updateLastSyncTime(Context context) {
        context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_KEY_LAST_TIME, System.currentTimeMillis())
                .apply();
    }

    private static String normalizeBaseUrl(String serverUrl) {
        if (serverUrl == null) return "";
        String url = serverUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        return url;
    }

    private static String getBackupFileUrl(String serverUrl) {
        String base = normalizeBaseUrl(serverUrl);
        return base + BACKUP_FILENAME;
    }

    private static void applyAuth(HttpURLConnection conn, String user, String pass) {
        if (user != null && !user.isEmpty()) {
            String credentials = user + ":" + (pass != null ? pass : "");
            String basicAuth = "Basic " + Base64.encodeToString(credentials.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            conn.setRequestProperty("Authorization", basicAuth);
        }
    }

    /**
     * Tests WebDAV connectivity and authorization.
     */
    public static SyncResult testConnection(String serverUrl, String user, String pass) {
        try {
            String base = normalizeBaseUrl(serverUrl);
            if (base.isEmpty()) {
                return new SyncResult(false, "WebDAV 服务器地址不能为空");
            }
            URL url = new URL(base);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PROPFIND");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            applyAuth(conn, user, pass);
            conn.setRequestProperty("Depth", "0");

            int code = conn.getResponseCode();
            if (code == 200 || code == 207 || code == 204) {
                conn.disconnect();
                return new SyncResult(true, "WebDAV 连接测试成功 (HTTP " + code + ")");
            }

            // Fallback test with GET if server disables PROPFIND on root
            conn.disconnect();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            applyAuth(conn, user, pass);
            code = conn.getResponseCode();
            conn.disconnect();

            if (code == 200 || code == 204 || code == 404 || code == 301 || code == 302) {
                return new SyncResult(true, "WebDAV 服务器联通正常 (HTTP " + code + ")");
            } else if (code == 401 || code == 403) {
                return new SyncResult(false, "WebDAV 鉴权失败: 用户名或密码错误 (HTTP " + code + ")");
            } else {
                return new SyncResult(false, "WebDAV 服务器响应异常 (HTTP " + code + ")");
            }
        } catch (Exception e) {
            return new SyncResult(false, "连接失败: " + (e.getMessage() != null ? e.getMessage() : "网络超时"));
        }
    }

    /**
     * Uploads all local subscriptions, favorites, and settings to WebDAV.
     */
    public static SyncResult uploadBackup(Context context, String serverUrl, String user, String pass) {
        try {
            String targetUrl = getBackupFileUrl(serverUrl);
            JSONObject backupData = SubscriptionManager.exportAllData(context);
            byte[] contentBytes = backupData.toString(2).getBytes(StandardCharsets.UTF_8);

            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            applyAuth(conn, user, pass);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Content-Length", String.valueOf(contentBytes.length));

            try (OutputStream out = conn.getOutputStream()) {
                out.write(contentBytes);
                out.flush();
            }

            int code = conn.getResponseCode();
            conn.disconnect();

            if (code >= 200 && code < 300) {
                updateLastSyncTime(context);
                return new SyncResult(true, "配置已成功上传至 WebDAV 云盘 (" + contentBytes.length + " 字节)");
            } else if (code == 401 || code == 403) {
                return new SyncResult(false, "上传失败: WebDAV 鉴权错误 (HTTP " + code + ")");
            } else {
                return new SyncResult(false, "WebDAV 上传失败 (HTTP " + code + ")");
            }
        } catch (Exception e) {
            return new SyncResult(false, "上传出错: " + (e.getMessage() != null ? e.getMessage() : "网络异常"));
        }
    }

    /**
     * Downloads backup from WebDAV and restores local subscriptions, favorites, and settings.
     */
    public static SyncResult downloadAndRestore(Context context, String serverUrl, String user, String pass) {
        try {
            String targetUrl = getBackupFileUrl(serverUrl);
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            applyAuth(conn, user, pass);

            int code = conn.getResponseCode();
            if (code == 404) {
                conn.disconnect();
                return new SyncResult(false, "云盘未找到备份文件: " + BACKUP_FILENAME);
            }
            if (code >= 400) {
                conn.disconnect();
                return new SyncResult(false, "下载备份失败 (HTTP " + code + ")");
            }

            StringBuilder sb = new StringBuilder();
            try (InputStream in = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            conn.disconnect();

            String jsonStr = sb.toString().trim();
            if (jsonStr.isEmpty() || !jsonStr.startsWith("{")) {
                return new SyncResult(false, "备份内容格式损坏或为空");
            }

            JSONObject root = new JSONObject(jsonStr);
            boolean imported = SubscriptionManager.importAllData(context, root);
            if (imported) {
                updateLastSyncTime(context);
                return new SyncResult(true, "已成功从 WebDAV 恢复全量订阅与偏好配置！");
            } else {
                return new SyncResult(false, "解析备份数据失败");
            }
        } catch (Exception e) {
            return new SyncResult(false, "同步恢复出错: " + (e.getMessage() != null ? e.getMessage() : "网络异常"));
        }
    }
}
