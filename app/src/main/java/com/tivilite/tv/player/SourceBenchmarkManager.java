package com.tivilite.tv.player;

import android.os.Handler;
import android.os.Looper;
import com.tivilite.tv.model.Channel;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SourceBenchmarkManager {

    private static final SourceBenchmarkManager INSTANCE = new SourceBenchmarkManager();
    public static SourceBenchmarkManager getInstance() { return INSTANCE; }

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> latencyCache = new ConcurrentHashMap<>();
    private final Map<String, Long> latencyTimestamp = new ConcurrentHashMap<>();

    public interface BenchmarkCallback {
        void onBenchmarkFinished(int bestIndex, long lowestLatencyMs);
    }

    public void benchmarkChannelSources(Channel channel, BenchmarkCallback callback) {
        if (channel == null || !channel.hasAlternativeUrls()) {
            if (callback != null) callback.onBenchmarkFinished(0, getCachedLatency(channel != null ? channel.getUrl() : ""));
            return;
        }

        executor.execute(() -> {
            java.util.List<String> urls = channel.getUrls();
            int bestIdx = 0;
            long minLatency = Long.MAX_VALUE;

            for (int i = 0; i < urls.size(); i++) {
                String u = urls.get(i);
                long lat = pingUrl(u);
                latencyCache.put(u, lat);
                latencyTimestamp.put(u, System.currentTimeMillis());

                if (lat > 0 && lat < minLatency) {
                    minLatency = lat;
                    bestIdx = i;
                }
            }

            final int finalBestIdx = bestIdx;
            final long finalMinLatency = minLatency == Long.MAX_VALUE ? -1 : minLatency;
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onBenchmarkFinished(finalBestIdx, finalMinLatency);
                }
            });
        });
    }

    public long getCachedLatency(String url) {
        if (url == null || url.isEmpty()) return -1;
        Long ts = latencyTimestamp.get(url);
        if (ts != null && System.currentTimeMillis() - ts < 10 * 60 * 1000) { // 10 minutes cache
            Long lat = latencyCache.get(url);
            return lat != null ? lat : -1;
        }
        return -1;
    }

    public String getLatencyBadge(String url) {
        long lat = getCachedLatency(url);
        if (lat <= 0) return "";
        if (lat < 120) return lat + "ms · 极速";
        if (lat < 350) return lat + "ms · 流畅";
        return lat + "ms · 一般";
    }

    private long pingUrl(String targetUrl) {
        if (targetUrl == null || !targetUrl.startsWith("http")) return -1;
        long start = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(1800);
            conn.setReadTimeout(1800);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV)");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code >= 200 && code < 400) {
                return System.currentTimeMillis() - start;
            }
        } catch (Exception e) {
            // Fallback: Some IPTV servers do not allow HEAD, attempt range GET
            try {
                if (conn != null) conn.disconnect();
                conn = (HttpURLConnection) new URL(targetUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Range", "bytes=0-1024");
                conn.setConnectTimeout(1800);
                conn.setReadTimeout(1800);
                int code = conn.getResponseCode();
                if (code >= 200 && code < 400) {
                    return System.currentTimeMillis() - start;
                }
            } catch (Exception ignored) {}
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
        return -1;
    }
}
