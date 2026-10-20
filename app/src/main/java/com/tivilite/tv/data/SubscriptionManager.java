package com.tivilite.tv.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.tivilite.tv.favorite.FavoriteManager;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                SmartChannelMatcher.enrichChannel(c);
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

    public static synchronized JSONObject exportAllData(Context context) {
        JSONObject root = new JSONObject();
        try {
            JSONArray subsArr = new JSONArray();
            for (Subscription s : getSubscriptions(context)) {
                subsArr.put(s.toJson());
            }
            root.put("subscriptions", subsArr);

            JSONArray favsArr = new JSONArray();
            Set<String> favs = FavoriteManager.getFavoriteNames(context);
            if (favs != null) {
                for (String f : favs) favsArr.put(f);
            }
            root.put("favorites", favsArr);

            SharedPreferences sp = context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE);
            JSONObject settings = new JSONObject();
            settings.put("decoder_mode", sp.getInt("decoder_mode", 0));
            settings.put("aspect_ratio_mode", sp.getInt("aspect_ratio_mode", 0));
            settings.put("buffer_mode", sp.getInt("buffer_mode", 0));
            settings.put("auto_boot_start", sp.getBoolean("auto_boot_start", true));
            settings.put("invert_up_down", sp.getBoolean("invert_up_down", false));
            settings.put("mini_drawer_preview", sp.getBoolean("mini_drawer_preview", true));
            settings.put("webdav_url", sp.getString("webdav_url", ""));
            settings.put("webdav_user", sp.getString("webdav_user", ""));
            settings.put("webdav_pass", sp.getString("webdav_pass", ""));
            settings.put("webdav_auto_sync", sp.getBoolean("webdav_auto_sync", false));
            root.put("settings", settings);
            root.put("version", "1.3.0");
            root.put("export_time", System.currentTimeMillis());
        } catch (Exception ignored) {}
        return root;
    }

    public static synchronized boolean importAllData(Context context, JSONObject root) {
        if (root == null) return false;
        try {
            if (root.has("subscriptions")) {
                JSONArray subsArr = root.getJSONArray("subscriptions");
                List<Subscription> current = getSubscriptions(context);
                for (int i = 0; i < subsArr.length(); i++) {
                    Subscription s = Subscription.fromJson(subsArr.getJSONObject(i));
                    boolean exists = false;
                    for (Subscription cur : current) {
                        if (cur.getId().equals(s.getId()) || (cur.getName().equals(s.getName()) && cur.getUrl().equals(s.getUrl()))) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) current.add(s);
                }
                saveSubscriptions(context, current);
            }

            if (root.has("favorites")) {
                JSONArray favsArr = root.getJSONArray("favorites");
                Set<String> favs = new HashSet<>(FavoriteManager.getFavoriteNames(context));
                for (int i = 0; i < favsArr.length(); i++) {
                    favs.add(favsArr.getString(i));
                }
                FavoriteManager.setFavoriteNames(context, favs);
            }

            if (root.has("settings")) {
                JSONObject s = root.getJSONObject("settings");
                SharedPreferences.Editor ed = context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE).edit();
                if (s.has("decoder_mode")) ed.putInt("decoder_mode", s.getInt("decoder_mode"));
                if (s.has("aspect_ratio_mode")) ed.putInt("aspect_ratio_mode", s.getInt("aspect_ratio_mode"));
                if (s.has("buffer_mode")) ed.putInt("buffer_mode", s.getInt("buffer_mode"));
                if (s.has("auto_boot_start")) ed.putBoolean("auto_boot_start", s.getBoolean("auto_boot_start"));
                if (s.has("invert_up_down")) ed.putBoolean("invert_up_down", s.getBoolean("invert_up_down"));
                if (s.has("mini_drawer_preview")) ed.putBoolean("mini_drawer_preview", s.getBoolean("mini_drawer_preview"));
                if (s.has("webdav_url")) ed.putString("webdav_url", s.getString("webdav_url"));
                if (s.has("webdav_user")) ed.putString("webdav_user", s.getString("webdav_user"));
                if (s.has("webdav_pass")) ed.putString("webdav_pass", s.getString("webdav_pass"));
                if (s.has("webdav_auto_sync")) ed.putBoolean("webdav_auto_sync", s.getBoolean("webdav_auto_sync"));
                ed.apply();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String normalizeChannelKey(String rawName) {
        if (rawName == null) return "";
        return SmartChannelMatcher.cleanKey(rawName);
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
