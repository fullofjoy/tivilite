package com.tivilite.tv.epg;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.tivilite.tv.model.Channel;
import com.tivilite.tv.model.Program;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class EpgManager {

    public interface EpgCallback {
        void onEpgLoaded(String channelName, Program currentProgram, List<Program> fullSchedule);
    }

    private static EpgManager instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, List<Program>> memoryCache = new HashMap<>();
    private static final String DEFAULT_EPG_API = "http://epg.51zmt.top:8000/api/diyp/";

    public static synchronized EpgManager getInstance() {
        if (instance == null) {
            instance = new EpgManager();
        }
        return instance;
    }

    public void loadEpgForChannel(Context context, Channel channel, EpgCallback callback) {
        if (channel == null || callback == null) return;
        String rawName = channel.getName();
        String standardName = standardizeName(rawName);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String cacheKey = standardName + "_" + today;

        // 1. Check memory cache
        synchronized (memoryCache) {
            if (memoryCache.containsKey(cacheKey)) {
                List<Program> list = memoryCache.get(cacheKey);
                Program current = findCurrentProgram(list);
                mainHandler.post(() -> callback.onEpgLoaded(rawName, current, list));
                return;
            }
        }

        // 2. Async fetch (Disk cache -> Network API -> Fallback schedule)
        new Thread(() -> {
            List<Program> programs = loadFromDisk(context, cacheKey);
            if (programs.isEmpty()) {
                programs = fetchFromNetwork(context, standardName, today);
            }
            if (programs.isEmpty()) {
                programs = generateFallbackSchedule(standardName);
            }

            // Cache in memory and disk
            if (!programs.isEmpty()) {
                synchronized (memoryCache) {
                    memoryCache.put(cacheKey, programs);
                }
                saveToDisk(context, cacheKey, programs);
            }

            Program current = findCurrentProgram(programs);
            List<Program> finalPrograms = programs;
            mainHandler.post(() -> callback.onEpgLoaded(rawName, current, finalPrograms));
        }).start();
    }

    private Program findCurrentProgram(List<Program> list) {
        if (list == null || list.isEmpty()) return null;
        long now = System.currentTimeMillis();
        for (Program p : list) {
            if (p.getStartTime() <= now && now < p.getEndTime()) {
                return p;
            }
        }
        return list.get(0);
    }

    private List<Program> fetchFromNetwork(Context context, String standardName, String dateStr) {
        List<Program> result = new ArrayList<>();
        String baseUrl = context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                .getString("epg_api_url", DEFAULT_EPG_API);

        try {
            String queryUrl = baseUrl + "?ch=" + URLEncoder.encode(standardName, "UTF-8") + "&date=" + dateStr;
            URL url = new URL(queryUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "TiviLite-TV/1.0");

            if (conn.getResponseCode() == 200) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                String resp = sb.toString();
                if (resp.trim().startsWith("{")) {
                    JSONObject root = new JSONObject(resp);
                    JSONArray arr = root.optJSONArray("epg_data");
                    if (arr != null && arr.length() > 0) {
                        SimpleDateFormat fullFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                        long now = System.currentTimeMillis();

                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject item = arr.getJSONObject(i);
                            String title = item.optString("title", "精彩节目");
                            String start = item.optString("start", "");
                            String end = item.optString("end", "");

                            if (!start.isEmpty() && !end.isEmpty()) {
                                Date startDate = fullFormat.parse(dateStr + " " + start);
                                Date endDate = fullFormat.parse(dateStr + " " + end);
                                if (startDate != null && endDate != null) {
                                    long st = startDate.getTime();
                                    long et = endDate.getTime();
                                    if (et <= st) et += 24 * 3600 * 1000L;
                                    boolean isCatchup = et < now;
                                    result.add(new Program(title, st, et, isCatchup));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Network failure or timeout, gracefully fall back
        }

        return result;
    }

    private List<Program> generateFallbackSchedule(String channelName) {
        List<Program> list = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long now = System.currentTimeMillis();

        String isNews = channelName.contains("新闻") || channelName.contains("cctv13") ? "新闻" : "";
        String[] mockTitles = {
                "晨间资讯与早间气象",
                "经典精选早间剧场",
                "焦点访谈与社会透视",
                "午间新闻与特别报道",
                "下午精选人文纪录片",
                "傍晚新闻直播间",
                "晚间新闻联播与重点播报",
                "黄金档热播精选剧场 (第1集)",
                "黄金档热播精选剧场 (第2集)",
                "晚间国际纵横深度对话",
                "深夜剧场与回顾精选"
        };

        // Generate past 72 hours of mock catchup slots
        long slotDuration = 2 * 3600 * 1000L;
        long startTime = now - (36 * 3600 * 1000L); // 1.5 days ago

        for (int i = 0; i < mockTitles.length * 2; i++) {
            long st = startTime + (i * slotDuration);
            long et = st + slotDuration;
            String t = mockTitles[i % mockTitles.length];
            if (channelName.contains("卫视")) {
                t = channelName + "·" + t;
            }
            boolean isCatchup = et < now;
            list.add(new Program(t, st, et, isCatchup));
            if (st <= now && now < et) {
                // Current live show
            }
        }

        return list;
    }

    public static String standardizeName(String raw) {
        if (raw == null) return "";
        String s = raw.toUpperCase()
                .replaceAll("[\\[\\]\\s\\-_（）()]", "")
                .replace("高清", "")
                .replace("超清", "")
                .replace("1080P", "")
                .replace("4K", "")
                .replace("8K", "")
                .replace("综合", "")
                .replace("新闻", "")
                .replace("中文国际", "")
                .replace("CCTV-", "CCTV")
                .trim();
        return s;
    }

    private List<Program> loadFromDisk(Context context, String key) {
        List<Program> list = new ArrayList<>();
        File dir = new File(context.getCacheDir(), "epg");
        File file = new File(dir, key + ".json");
        if (!file.exists()) return list;

        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
            JSONArray arr = new JSONArray(sb.toString());
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                long st = obj.optLong("start");
                long et = obj.optLong("end");
                list.add(new Program(obj.optString("title"), st, et, et < now));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void saveToDisk(Context context, String key, List<Program> list) {
        try {
            File dir = new File(context.getCacheDir(), "epg");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, key + ".json");
            JSONArray arr = new JSONArray();
            for (Program p : list) {
                JSONObject obj = new JSONObject();
                obj.put("title", p.getTitle());
                obj.put("start", p.getStartTime());
                obj.put("end", p.getEndTime());
                arr.put(obj);
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }
}
