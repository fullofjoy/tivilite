package com.tivilite.tv.data;

import android.content.Context;
import com.tivilite.tv.model.Channel;
import com.tivilite.tv.parser.M3UParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public class SubscriptionManager {

    public static class Subscription {
        private String id;
        private String name;
        private String url;
        private String content;
        private int channelCount;
        private boolean enabled;
        private long updatedAt;

        public Subscription(String id, String name, String url, String content, int channelCount, boolean enabled, long updatedAt) {
            this.id = id != null ? id : UUID.randomUUID().toString();
            this.name = name != null && !name.trim().isEmpty() ? name : "自定义订阅";
            this.url = url != null ? url : "";
            this.content = content != null ? content : "";
            this.channelCount = channelCount;
            this.enabled = enabled;
            this.updatedAt = updatedAt > 0 ? updatedAt : System.currentTimeMillis();
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public int getChannelCount() { return channelCount; }
        public void setChannelCount(int count) { this.channelCount = count; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("name", name);
                obj.put("url", url);
                obj.put("content", content);
                obj.put("channel_count", channelCount);
                obj.put("enabled", enabled);
                obj.put("updated_at", updatedAt);
            } catch (Exception ignored) {}
            return obj;
        }

        public static Subscription fromJson(JSONObject obj) {
            return new Subscription(
                    obj.optString("id", UUID.randomUUID().toString()),
                    obj.optString("name", "自定义订阅"),
                    obj.optString("url", ""),
                    obj.optString("content", ""),
                    obj.optInt("channel_count", 0),
                    obj.optBoolean("enabled", true),
                    obj.optLong("updated_at", System.currentTimeMillis())
            );
        }
    }

    private static final String SUBS_FILE = "subscriptions.json";
    private static final String LEGACY_PLAYLIST = "playlist.m3u";

    public static synchronized List<Subscription> getSubscriptions(Context context) {
        List<Subscription> list = new ArrayList<>();
        File file = new File(context.getFilesDir(), SUBS_FILE);

        if (!file.exists()) {
            // Check legacy playlist.m3u migration
            File legacy = new File(context.getFilesDir(), LEGACY_PLAYLIST);
            if (legacy.exists()) {
                String content = readFileContent(legacy);
                if (!content.trim().isEmpty()) {
                    List<Channel> parsed = M3UParser.parse(content);
                    Subscription sub = new Subscription(
                            "sub_legacy",
                            "本地导入订阅 (已聚合)",
                            "",
                            content,
                            parsed.size(),
                            true,
                            System.currentTimeMillis()
                    );
                    list.add(sub);
                    saveSubscriptions(context, list);
                    return list;
                }
            }
            return list;
        }

        String jsonStr = readFileContent(file);
        if (jsonStr.trim().isEmpty()) return list;

        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Subscription.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    public static synchronized void saveSubscriptions(Context context, List<Subscription> list) {
        JSONArray arr = new JSONArray();
        for (Subscription sub : list) {
            arr.put(sub.toJson());
        }
        writeFileContent(new File(context.getFilesDir(), SUBS_FILE), arr.toString());
    }

    public static synchronized void addOrUpdateSubscription(Context context, Subscription sub, boolean overwriteOthers) {
        List<Subscription> list = overwriteOthers ? new ArrayList<>() : getSubscriptions(context);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            Subscription s = list.get(i);
            if (s.getId().equals(sub.getId()) || (!sub.getUrl().isEmpty() && sub.getUrl().equals(s.getUrl()))) {
                list.set(i, sub);
                found = true;
                break;
            }
        }
        if (!found) {
            list.add(sub);
        }
        saveSubscriptions(context, list);
    }

    public static synchronized void deleteSubscription(Context context, String id) {
        List<Subscription> list = getSubscriptions(context);
        List<Subscription> updated = new ArrayList<>();
        for (Subscription s : list) {
            if (!s.getId().equals(id)) {
                updated.add(s);
            }
        }
        saveSubscriptions(context, updated);
    }

    public static synchronized void toggleSubscription(Context context, String id, boolean enabled) {
        List<Subscription> list = getSubscriptions(context);
        for (Subscription s : list) {
            if (s.getId().equals(id)) {
                s.setEnabled(enabled);
                break;
            }
        }
        saveSubscriptions(context, list);
    }

    public static synchronized void clearAllSubscriptions(Context context) {
        saveSubscriptions(context, new ArrayList<>());
        File legacy = new File(context.getFilesDir(), LEGACY_PLAYLIST);
        if (legacy.exists()) legacy.delete();
    }

    /**
     * 核心多订阅聚合引擎：将所有启用的订阅列表解析并按标准化频道名合并去重，生成多线路频道
     */
    public static synchronized List<Channel> getAggregatedChannels(Context context) {
        List<Subscription> subs = getSubscriptions(context);
        Map<String, Channel> channelMap = new LinkedHashMap<>();
        int channelSeq = 1;

        for (Subscription sub : subs) {
            if (!sub.isEnabled() || sub.getContent() == null || sub.getContent().trim().isEmpty()) {
                continue;
            }
            List<Channel> channels = M3UParser.parse(sub.getContent());
            for (Channel c : channels) {
                String key = normalizeChannelKey(c.getName());
                if (channelMap.containsKey(key)) {
                    Channel existing = channelMap.get(key);
                    if (existing != null) {
                        for (String u : c.getUrls()) {
                            existing.addUrl(u);
                        }
                        if ((existing.getLogoUrl() == null || existing.getLogoUrl().isEmpty()) && c.getLogoUrl() != null) {
                            existing.setLogoUrl(c.getLogoUrl());
                        }
                        if (!existing.hasCatchup() && c.hasCatchup()) {
                            existing.setCatchupSource(c.getCatchupSource());
                            existing.setCatchupType(c.getCatchupType());
                        }
                    }
                } else {
                    c.setNumber(channelSeq++);
                    channelMap.put(key, c);
                }
            }
        }

        List<Channel> result = new ArrayList<>(channelMap.values());
        // Cache to legacy playlist.m3u for fast boot and backward compatibility
        if (!result.isEmpty()) {
            StringBuilder sb = new StringBuilder("#EXTM3U\n");
            for (Channel ch : result) {
                sb.append("#EXTINF:-1 tvg-name=\"").append(ch.getName()).append("\"");
                if (ch.getLogoUrl() != null && !ch.getLogoUrl().isEmpty()) {
                    sb.append(" tvg-logo=\"").append(ch.getLogoUrl()).append("\"");
                }
                if (ch.getGroup() != null && !ch.getGroup().isEmpty()) {
                    sb.append(" group-title=\"").append(ch.getGroup()).append("\"");
                }
                if (ch.getCatchupSource() != null && !ch.getCatchupSource().isEmpty()) {
                    sb.append(" catchup-source=\"").append(ch.getCatchupSource()).append("\"");
                }
                sb.append(",").append(ch.getName()).append("\n");
                for (String u : ch.getUrls()) {
                    sb.append(u).append("\n");
                }
            }
            writeFileContent(new File(context.getFilesDir(), LEGACY_PLAYLIST), sb.toString());
        }

        return result;
    }

    public static String normalizeChannelKey(String rawName) {
        if (rawName == null) return "";
        return rawName.toLowerCase()
                .replaceAll("[\\[\\]\\s\\-_（）()]", "")
                .replace("高清", "")
                .replace("超清", "")
                .replace("标清", "")
                .replace("1080p", "")
                .replace("4k", "")
                .replace("8k", "")
                .replace("cctv-", "cctv")
                .trim();
    }

    private static String readFileContent(File file) {
        if (!file.exists()) return "";
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        } catch (Exception ignored) {}
        return sb.toString();
    }

    private static void writeFileContent(File file, String content) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }
}
