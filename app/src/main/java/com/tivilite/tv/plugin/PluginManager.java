package com.tivilite.tv.plugin;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * TiviLite Geek Plugin Engine & Dynamic Source Resolver
 * Provides an extensible architecture for dynamic stream URL resolution,
 * anti-hotlink header injection (User-Agent, Referer, Cookies), and custom live platform resolvers.
 */
public class PluginManager {
    private static final String TAG = "PluginManager";
    private static final String PREF_NAME = "tivilite_plugins";
    private static final String KEY_PLUGIN_LIST = "installed_plugins";

    public static class ResolvedStream {
        public final String url;
        public final Map<String, String> headers;
        public final String appliedPluginName;

        public ResolvedStream(String url, Map<String, String> headers, String appliedPluginName) {
            this.url = url;
            this.headers = headers != null ? headers : new HashMap<>();
            this.appliedPluginName = appliedPluginName;
        }
    }

    public static class PluginRule {
        public String id;
        public String name;
        public String author;
        public String description;
        public boolean enabled;
        public String urlPattern; // Regex
        public Map<String, String> headers = new HashMap<>();

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("name", name);
                obj.put("author", author != null ? author : "极客开发者");
                obj.put("description", description != null ? description : "");
                obj.put("enabled", enabled);
                obj.put("urlPattern", urlPattern);
                JSONObject hdrObj = new JSONObject();
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    hdrObj.put(e.getKey(), e.getValue());
                }
                obj.put("headers", hdrObj);
                return obj;
            } catch (Exception e) {
                return null;
            }
        }

        public static PluginRule fromJson(JSONObject obj) {
            if (obj == null) return null;
            try {
                PluginRule rule = new PluginRule();
                rule.id = obj.optString("id", "plugin_" + System.currentTimeMillis());
                rule.name = obj.optString("name", "未命名插件");
                rule.author = obj.optString("author", "社区提供");
                rule.description = obj.optString("description", "");
                rule.enabled = obj.optBoolean("enabled", true);
                rule.urlPattern = obj.optString("urlPattern", ".*");
                JSONObject hdrs = obj.optJSONObject("headers");
                if (hdrs != null) {
                    Iterator<String> keys = hdrs.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        rule.headers.put(k, hdrs.getString(k));
                    }
                }
                return rule;
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static volatile PluginManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final List<PluginRule> plugins = new ArrayList<>();

    public static PluginManager getInstance(Context context) {
        if (instance == null) {
            synchronized (PluginManager.class) {
                if (instance == null) {
                    instance = new PluginManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private PluginManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadPlugins();
        if (plugins.isEmpty()) {
            initDefaultPlugins();
        }
    }

    private void initDefaultPlugins() {
        // 1. Bilibili Live Stream & Anti-Hotlinking Resolver
        PluginRule bili = new PluginRule();
        bili.id = "bilibili_live_resolver";
        bili.name = "哔哩哔哩直播解密与 Referer 穿透";
        bili.author = "TiviLite 官方团队";
        bili.description = "自动注入 B站 Referer 与桌面端 User-Agent，防止 403 盗链拦截";
        bili.enabled = true;
        bili.urlPattern = ".*bilibili\\.com/.*|.*bilivideo\\.com/.*|bilibili://.*";
        bili.headers.put("Referer", "https://live.bilibili.com/");
        bili.headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        plugins.add(bili);

        // 2. Douyu & Huya Stream Resolver
        PluginRule liveStream = new PluginRule();
        liveStream.id = "douyu_huya_resolver";
        liveStream.name = "斗鱼与虎牙直播流智能伪装";
        liveStream.author = "TiviLite 官方团队";
        liveStream.description = "注入专属 Origin 校验头，保障游戏赛事直播流稳定长效播放";
        liveStream.enabled = true;
        liveStream.urlPattern = ".*douyucdn\\.cn/.*|.*huya\\.com/.*|.*douyu\\.com/.*";
        liveStream.headers.put("Origin", "https://www.douyu.com");
        liveStream.headers.put("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");
        plugins.add(liveStream);

        // 3. CCTV/Provincial Official CDN Token Injector
        PluginRule cdnEnhance = new PluginRule();
        cdnEnhance.id = "general_iptv_opt";
        cdnEnhance.name = "运营商专线与央视源低延迟传输优化";
        cdnEnhance.author = "TiviLite 官方团队";
        cdnEnhance.description = "注入宽带连接保持与首包加速传输头部，减少换台 TCP 握手开销";
        cdnEnhance.enabled = true;
        cdnEnhance.urlPattern = ".*cctv.*|.*yangshipin.*|.*gitv.*";
        cdnEnhance.headers.put("Connection", "keep-alive");
        cdnEnhance.headers.put("X-Forwarded-For", "116.228.111.18");
        plugins.add(cdnEnhance);

        savePlugins();
    }

    private synchronized void loadPlugins() {
        plugins.clear();
        String jsonStr = prefs.getString(KEY_PLUGIN_LIST, null);
        if (jsonStr != null && !jsonStr.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    PluginRule rule = PluginRule.fromJson(arr.getJSONObject(i));
                    if (rule != null) plugins.add(rule);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load plugins JSON", e);
            }
        }
    }

    public synchronized void savePlugins() {
        try {
            JSONArray arr = new JSONArray();
            for (PluginRule r : plugins) {
                JSONObject o = r.toJson();
                if (o != null) arr.put(o);
            }
            prefs.edit().putString(KEY_PLUGIN_LIST, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save plugins", e);
        }
    }

    public synchronized ResolvedStream resolve(String originalUrl) {
        if (originalUrl == null || originalUrl.isEmpty()) {
            return new ResolvedStream(originalUrl, null, null);
        }

        String targetUrl = originalUrl;
        Map<String, String> combinedHeaders = new HashMap<>();
        String appliedPlugin = null;

        // Custom protocol dynamic resolution (e.g. bilibili://3 or douyu://9999)
        if (originalUrl.startsWith("bilibili://")) {
            String roomId = originalUrl.replace("bilibili://", "").trim();
            targetUrl = "https://live.bilibili.com/" + roomId;
        }

        for (PluginRule rule : plugins) {
            if (!rule.enabled || rule.urlPattern == null) continue;
            try {
                if (Pattern.compile(rule.urlPattern, Pattern.CASE_INSENSITIVE).matcher(targetUrl).find()) {
                    combinedHeaders.putAll(rule.headers);
                    appliedPlugin = rule.name;
                }
            } catch (Exception ignored) {}
        }

        return new ResolvedStream(targetUrl, combinedHeaders, appliedPlugin);
    }

    public synchronized List<PluginRule> getAllPlugins() {
        return new ArrayList<>(plugins);
    }

    public synchronized JSONArray getPluginsJson() {
        JSONArray arr = new JSONArray();
        for (PluginRule r : plugins) {
            JSONObject o = r.toJson();
            if (o != null) arr.put(o);
        }
        return arr;
    }

    public synchronized boolean togglePlugin(String id, boolean enabled) {
        for (PluginRule r : plugins) {
            if (r.id.equals(id)) {
                r.enabled = enabled;
                savePlugins();
                return true;
            }
        }
        return false;
    }

    public synchronized boolean addOrUpdatePlugin(PluginRule rule) {
        if (rule == null || rule.id == null) return false;
        for (int i = 0; i < plugins.size(); i++) {
            if (plugins.get(i).id.equals(rule.id)) {
                plugins.set(i, rule);
                savePlugins();
                return true;
            }
        }
        plugins.add(rule);
        savePlugins();
        return true;
    }

    public synchronized boolean deletePlugin(String id) {
        for (int i = 0; i < plugins.size(); i++) {
            if (plugins.get(i).id.equals(id)) {
                plugins.remove(i);
                savePlugins();
                return true;
            }
        }
        return false;
    }
}
