package com.tivilite.tv.update;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.tivilite.tv.BuildConfig;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public class UpdateManager {

    private static final String API_URL = "https://api.github.com/repos/fullofjoy/tivilite/releases/latest";
    private static final String MIRROR_API_URL = "https://ghfast.top/https://api.github.com/repos/fullofjoy/tivilite/releases/latest";
    private static final Handler handler = new Handler(Looper.getMainLooper());

    public static void checkUpdate(Context context, boolean isManual) {
        if (isManual) {
            Toast.makeText(context, "🔍 正在检查最新版本...", Toast.LENGTH_SHORT).show();
        }

        new Thread(() -> {
            JSONObject releaseObj = fetchReleaseJson(API_URL);
            if (releaseObj == null) {
                releaseObj = fetchReleaseJson(MIRROR_API_URL);
            }

            if (releaseObj == null) {
                if (isManual) {
                    handler.post(() -> Toast.makeText(context, "检查更新失败，请检查网络连接", Toast.LENGTH_SHORT).show());
                }
                return;
            }

            try {
                String tagName = releaseObj.optString("tag_name", "");
                String releaseName = releaseObj.optString("name", tagName);
                String releaseNotes = releaseObj.optString("body", "常规性能优化与体验改进");
                String cleanRemoteVer = tagName.replace("v", "").replace("V", "").trim();
                String cleanLocalVer = BuildConfig.VERSION_NAME.replace("v", "").replace("V", "").trim();

                String apkUrl = "";
                JSONArray assets = releaseObj.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", "");
                            break;
                        }
                    }
                }

                final String finalApkUrl = apkUrl;
                if (isNewer(cleanRemoteVer, cleanLocalVer) && !finalApkUrl.isEmpty()) {
                    handler.post(() -> showUpdateDialog(context, tagName, releaseName, releaseNotes, finalApkUrl));
                } else {
                    if (isManual) {
                        handler.post(() -> Toast.makeText(context, "🎉 当前已是最新版本 (v" + BuildConfig.VERSION_NAME + ")", Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static JSONObject fetchReleaseJson(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "TiviLite-App");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                    return new JSONObject(sb.toString());
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static boolean isNewer(String remote, String local) {
        try {
            String[] rParts = remote.split("\\.");
            String[] lParts = local.split("\\.");
            int maxLen = Math.max(rParts.length, lParts.length);
            for (int i = 0; i < maxLen; i++) {
                int r = i < rParts.length ? Integer.parseInt(rParts[i]) : 0;
                int l = i < lParts.length ? Integer.parseInt(lParts[i]) : 0;
                if (r > l) return true;
                if (r < l) return false;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void showUpdateDialog(Context context, String tag, String title, String notes, String downloadUrl) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("🚀 发现新版本 " + tag);
        builder.setMessage("新版亮点与特性：\n\n" + notes + "\n\n是否立即下载并无缝更新？");
        builder.setPositiveButton("立即在线升级", (dialog, which) -> downloadAndInstall(context, downloadUrl, tag));
        builder.setNegativeButton("稍后再说", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private static void downloadAndInstall(Context context, String downloadUrl, String tag) {
        ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setTitle("正在下载更新");
        progressDialog.setMessage("正在下载 TiviLite " + tag + " 安装包，请稍候...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(false);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            File cacheDir = context.getExternalCacheDir() != null ? context.getExternalCacheDir() : context.getCacheDir();
            File apkFile = new File(cacheDir, "tivilite_update.apk");

            boolean success = tryDownloadUrl(downloadUrl, apkFile, progressDialog);
            if (!success && downloadUrl.startsWith("https://github.com/")) {
                handler.post(() -> progressDialog.setMessage("直连较慢，正在切换国内高速镜像下载..."));
                String mirrorUrl = "https://ghfast.top/" + downloadUrl;
                success = tryDownloadUrl(mirrorUrl, apkFile, progressDialog);
            }

            if (success && apkFile.exists() && apkFile.length() > 0) {
                handler.post(() -> {
                    progressDialog.dismiss();
                    installApk(context, apkFile);
                });
            } else {
                handler.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(context, "下载更新失败，请检查网络或稍后重试", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static boolean tryDownloadUrl(String urlStr, File apkFile, ProgressDialog progressDialog) {
        HttpURLConnection conn = null;
        try {
            if (apkFile.exists()) apkFile.delete();
            String targetUrl = urlStr;
            for (int redirect = 0; redirect < 5; redirect++) {
                URL u = new URL(targetUrl);
                conn = (HttpURLConnection) u.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV Box) AppleWebKit/537.36");
                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                    String loc = conn.getHeaderField("Location");
                    if (loc != null && !loc.isEmpty()) {
                        targetUrl = loc;
                        conn.disconnect();
                        continue;
                    }
                }
                if (code == 200) {
                    int fileLength = conn.getContentLength();
                    try (InputStream in = conn.getInputStream();
                         FileOutputStream fos = new FileOutputStream(apkFile)) {
                        byte[] buffer = new byte[8192];
                        long total = 0;
                        int count;
                        while ((count = in.read(buffer)) != -1) {
                            total += count;
                            fos.write(buffer, 0, count);
                            if (fileLength > 0) {
                                int progress = (int) ((total * 100) / fileLength);
                                handler.post(() -> progressDialog.setProgress(progress));
                            }
                        }
                        fos.flush();
                    }
                    return true;
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) conn.disconnect();
        }
        return false;
    }

    public static void installApk(Context context, File apkFile) {
        if (!apkFile.exists()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apkFile);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            } else {
                intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            }
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "启动安装器异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
