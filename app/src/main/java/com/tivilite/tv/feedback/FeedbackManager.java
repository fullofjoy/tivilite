package com.tivilite.tv.feedback;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.tivilite.tv.BuildConfig;
import com.tivilite.tv.model.Channel;
import com.tivilite.tv.player.PlayerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TiviLite One-Click Issue Feedback and Optimization Submission Manager
 * Collects runtime device diagnostics, stores feedback locally, and generates GitHub Issue pre-filled URLs.
 */
public class FeedbackManager {
    private static final String TAG = "FeedbackManager";
    private static final String PREFS_NAME = "tivilite_feedback_prefs";
    private static final String KEY_FEEDBACK_LIST = "feedback_history_list";
    private static final String GITHUB_REPO = "fullofjoy/tivilite";

    public static class FeedbackItem {
        public String id;
        public long timestamp;
        public String type;
        public String content;
        public String contact;
        public String diagnostic;

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("timestamp", timestamp);
                obj.put("type", type != null ? type : "综合反馈");
                obj.put("content", content != null ? content : "");
                obj.put("contact", contact != null ? contact : "");
                obj.put("diagnostic", diagnostic != null ? diagnostic : "");
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static FeedbackItem fromJson(JSONObject obj) {
            if (obj == null) return null;
            FeedbackItem item = new FeedbackItem();
            item.id = obj.optString("id", String.valueOf(System.currentTimeMillis()));
            item.timestamp = obj.optLong("timestamp", System.currentTimeMillis());
            item.type = obj.optString("type", "综合反馈");
            item.content = obj.optString("content", "");
            item.contact = obj.optString("contact", "");
            item.diagnostic = obj.optString("diagnostic", "");
            return item;
        }
    }

    private static volatile FeedbackManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final List<FeedbackItem> feedbackHistory = new ArrayList<>();

    public static FeedbackManager getInstance(Context context) {
        if (instance == null) {
            synchronized (FeedbackManager.class) {
                if (instance == null) {
                    instance = new FeedbackManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private FeedbackManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadHistory();
    }

    private synchronized void loadHistory() {
        feedbackHistory.clear();
        String json = prefs.getString(KEY_FEEDBACK_LIST, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    FeedbackItem item = FeedbackItem.fromJson(arr.getJSONObject(i));
                    if (item != null) feedbackHistory.add(item);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load feedback history", e);
            }
        }
    }

    public synchronized void saveFeedback(String type, String content, String contact, String diagnostic) {
        FeedbackItem item = new FeedbackItem();
        item.id = "fb_" + System.currentTimeMillis();
        item.timestamp = System.currentTimeMillis();
        item.type = (type != null && !type.trim().isEmpty()) ? type.trim() : "问题反馈";
        item.content = (content != null && !content.trim().isEmpty()) ? content.trim() : "用户一键上报";
        item.contact = (contact != null) ? contact.trim() : "";
        item.diagnostic = (diagnostic != null) ? diagnostic : collectSystemDiagnostic(null, null);

        feedbackHistory.add(0, item);
        if (feedbackHistory.size() > 50) {
            feedbackHistory.remove(feedbackHistory.size() - 1);
        }

        try {
            JSONArray arr = new JSONArray();
            for (FeedbackItem fi : feedbackHistory) {
                arr.put(fi.toJson());
            }
            prefs.edit().putString(KEY_FEEDBACK_LIST, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist feedback item", e);
        }
    }

    public synchronized List<FeedbackItem> getFeedbackHistory() {
        return new ArrayList<>(feedbackHistory);
    }

    public synchronized JSONArray getFeedbackHistoryJson() {
        JSONArray arr = new JSONArray();
        for (FeedbackItem item : feedbackHistory) {
            arr.put(item.toJson());
        }
        return arr;
    }

    /**
     * Automatically captures comprehensive device, app, network and playback diagnostics.
     */
    public String collectSystemDiagnostic(PlayerManager playerManager, Channel currentChannel) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sb.append("【TiviLite 诊断快照 - ").append(sdf.format(new Date())).append("】\n");
        sb.append("• 应用版本: v").append(BuildConfig.VERSION_NAME).append(" (Code ").append(BuildConfig.VERSION_CODE).append(")\n");
        sb.append("• 硬件设备: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(" (").append(Build.PRODUCT).append(")\n");
        sb.append("• 操作系统: Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("• 架构支持: ").append(Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "未知").append("\n");

        // Memory info
        try {
            Runtime rt = Runtime.getRuntime();
            long maxMem = rt.maxMemory() / 1024 / 1024;
            long totalMem = rt.totalMemory() / 1024 / 1024;
            long freeMem = rt.freeMemory() / 1024 / 1024;
            sb.append("• 运行内存: JVM已用 ").append(totalMem - freeMem).append("MB / 最大 ").append(maxMem).append("MB\n");
        } catch (Exception ignored) {}

        // Channel info
        if (currentChannel != null) {
            sb.append("• 当前频道: ").append(currentChannel.getName())
              .append(" [").append(currentChannel.getGroup()).append("] (台号: ").append(currentChannel.getNumber()).append(")\n");
            sb.append("• 播放链接: ").append(currentChannel.getUrl()).append("\n");
            if (currentChannel.hasAlternativeUrls()) {
                sb.append("• 聚合线路: ").append(currentChannel.getSourceInfo()).append("\n");
            }
        } else {
            sb.append("• 当前频道: 未在播放状态\n");
        }

        // Playback Engine info
        if (playerManager != null) {
            sb.append("• 画面比例: ").append(playerManager.getAspectRatioModeText()).append("\n");
            sb.append("• 播放状态: ").append(playerManager.isPlaying() ? "正在播放" : "已暂停/缓冲").append("\n");
            sb.append("• 时移模式: ").append(playerManager.isCatchupMode() ? "7天时移回看" : "实时直播").append("\n");
        }

        return sb.toString();
    }

    /**
     * Generates a pre-filled GitHub Issue URL template that the user can open directly.
     */
    public String generateGitHubIssueUrl(String type, String content, String contact, String diagnostic) {
        try {
            String title = "[用户反馈] " + (type != null && !type.isEmpty() ? "[" + type + "] " : "") + (content != null && content.length() > 20 ? content.substring(0, 20) + "..." : content);
            StringBuilder body = new StringBuilder();
            body.append("### 📌 反馈分类\n").append(type != null ? type : "问题反馈").append("\n\n");
            body.append("### 📝 问题或优化建议描述\n").append(content != null ? content : "（请在此详述遇到的异常现象或优化建议）").append("\n\n");
            if (contact != null && !contact.isEmpty()) {
                body.append("### 📬 联系方式 (选填)\n").append(contact).append("\n\n");
            }
            body.append("### 💻 设备与运行时系统诊断快照 (自动采集)\n```text\n");
            body.append(diagnostic != null ? diagnostic : collectSystemDiagnostic(null, null));
            body.append("\n```\n\n*(此 Issue 由 TiviLite 灵动TV 客户端一键生成)*");

            return "https://github.com/" + GITHUB_REPO + "/issues/new?title="
                    + URLEncoder.encode(title, "UTF-8")
                    + "&body=" + URLEncoder.encode(body.toString(), "UTF-8");
        } catch (Exception e) {
            return "https://github.com/" + GITHUB_REPO + "/issues/new";
        }
    }

    /**
     * Exports a diagnostic report file to local cache/download directory.
     */
    public File exportReportFile(String type, String content, String diagnostic) {
        try {
            File exportDir = context.getExternalFilesDir("reports");
            if (exportDir == null) exportDir = context.getCacheDir();
            if (!exportDir.exists()) exportDir.mkdirs();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String fileName = "tivilite_feedback_" + sdf.format(new Date()) + ".txt";
            File reportFile = new File(exportDir, fileName);

            StringBuilder sb = new StringBuilder();
            sb.append("=========================================\n");
            sb.append("       TiviLite 灵动TV 问题与诊断报告      \n");
            sb.append("=========================================\n\n");
            sb.append("反馈类型: ").append(type != null ? type : "综合反馈").append("\n");
            sb.append("描述内容: ").append(content != null ? content : "").append("\n\n");
            sb.append(diagnostic != null ? diagnostic : collectSystemDiagnostic(null, null));
            sb.append("\n\n请将此文件发送给开发者，或提交至 GitHub Issues: https://github.com/fullofjoy/tivilite/issues\n");

            try (FileOutputStream fos = new FileOutputStream(reportFile)) {
                fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            return reportFile;
        } catch (Exception e) {
            Log.e(TAG, "Failed to export report file", e);
            return null;
        }
    }
}
