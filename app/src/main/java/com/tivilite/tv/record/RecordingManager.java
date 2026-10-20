package com.tivilite.tv.record;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.tivilite.tv.model.Channel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Live DVR and Stream Recording Manager for TiviLite.
 * Supports recording both direct HTTP-TS streams and live HLS (.m3u8) streams.
 * Stores recordings in standard external files directory accessible for direct playback and web download.
 */
public class RecordingManager {

    private static volatile RecordingManager instance;

    public static RecordingManager getInstance() {
        if (instance == null) {
            synchronized (RecordingManager.class) {
                if (instance == null) {
                    instance = new RecordingManager();
                }
            }
        }
        return instance;
    }

    public interface RecordingListener {
        void onRecordingStarted(File outputFile, Channel channel);
        void onRecordingProgress(long elapsedMs, long bytesRecorded, String formattedRate);
        void onRecordingFinished(File outputFile, long durationMs, long bytesRecorded);
        void onRecordingError(String errorMessage);
    }

    public static class RecordingItem {
        public final String fileName;
        public final String filePath;
        public final long fileSizeBytes;
        public final long lastModified;

        public RecordingItem(String fileName, String filePath, long fileSizeBytes, long lastModified) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.fileSizeBytes = fileSizeBytes;
            this.lastModified = lastModified;
        }

        public String getFormattedSize() {
            if (fileSizeBytes < 1024 * 1024) {
                return String.format(Locale.getDefault(), "%.1f KB", fileSizeBytes / 1024.0);
            } else if (fileSizeBytes < 1024 * 1024 * 1024) {
                return String.format(Locale.getDefault(), "%.1f MB", fileSizeBytes / (1024.0 * 1024.0));
            } else {
                return String.format(Locale.getDefault(), "%.2f GB", fileSizeBytes / (1024.0 * 1024.0 * 1024.0));
            }
        }

        public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            return sdf.format(new Date(lastModified));
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("name", fileName);
                obj.put("path", filePath);
                obj.put("size", fileSizeBytes);
                obj.put("size_formatted", getFormattedSize());
                obj.put("time", lastModified);
                obj.put("date_formatted", getFormattedDate());
            } catch (Exception ignored) {}
            return obj;
        }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private Thread recordingThread;

    private Channel currentRecordingChannel;
    private File currentOutputFile;
    private long recordingStartTime = 0;
    private long totalBytesRecorded = 0;
    private RecordingListener activeListener;

    private RecordingManager() {}

    public synchronized boolean isRecording() {
        return isRecording.get();
    }

    public synchronized Channel getCurrentRecordingChannel() {
        return currentRecordingChannel;
    }

    public synchronized File getCurrentOutputFile() {
        return currentOutputFile;
    }

    public synchronized long getRecordingStartTime() {
        return recordingStartTime;
    }

    public synchronized long getTotalBytesRecorded() {
        return totalBytesRecorded;
    }

    public synchronized void setListener(RecordingListener listener) {
        this.activeListener = listener;
    }

    public static File getRecordingsDir(Context context) {
        File dir = context.getExternalFilesDir("Recordings");
        if (dir == null) {
            dir = new File(context.getFilesDir(), "Recordings");
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Start recording the current playing channel stream.
     */
    public synchronized boolean startRecording(Context context, Channel channel, String streamUrl, RecordingListener listener) {
        if (isRecording.get()) {
            return false;
        }
        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            if (listener != null) listener.onRecordingError("无效的录制源地址");
            return false;
        }

        this.currentRecordingChannel = channel;
        this.activeListener = listener;
        this.recordingStartTime = System.currentTimeMillis();
        this.totalBytesRecorded = 0;
        this.isRecording.set(true);

        File dir = getRecordingsDir(context);
        String channelNameSafe = (channel != null ? channel.getName() : "Live")
                .replaceAll("[\\\\/:*?\"<>|\\s]", "_");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String fileName = channelNameSafe + "_" + sdf.format(new Date()) + ".ts";
        this.currentOutputFile = new File(dir, fileName);

        if (listener != null) {
            listener.onRecordingStarted(currentOutputFile, channel);
        }

        recordingThread = new Thread(() -> recordWorker(streamUrl, currentOutputFile));
        recordingThread.setPriority(Thread.NORM_PRIORITY);
        recordingThread.start();
        return true;
    }

    /**
     * Stop active recording and finalize the output file.
     */
    public synchronized void stopRecording() {
        if (!isRecording.get()) return;
        isRecording.set(false);
        if (recordingThread != null) {
            recordingThread.interrupt();
        }
    }

    private void recordWorker(String streamUrl, File outputFile) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(outputFile, true);
            boolean isHls = streamUrl.toLowerCase(Locale.ROOT).contains(".m3u8");

            if (isHls) {
                recordHlsStream(streamUrl, fos);
            } else {
                recordDirectStream(streamUrl, fos);
            }

            final long finalDuration = System.currentTimeMillis() - recordingStartTime;
            final long finalBytes = totalBytesRecorded;
            mainHandler.post(() -> {
                if (activeListener != null) {
                    activeListener.onRecordingFinished(outputFile, finalDuration, finalBytes);
                }
            });

        } catch (InterruptedException e) {
            // Normal recording stop requested by user
            final long finalDuration = System.currentTimeMillis() - recordingStartTime;
            final long finalBytes = totalBytesRecorded;
            mainHandler.post(() -> {
                if (activeListener != null) {
                    activeListener.onRecordingFinished(outputFile, finalDuration, finalBytes);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            final String err = e.getMessage() != null ? e.getMessage() : "录制中断";
            mainHandler.post(() -> {
                if (activeListener != null) {
                    activeListener.onRecordingError(err);
                }
            });
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
            isRecording.set(false);
        }
    }

    /**
     * Records direct progressive or TS HTTP stream by streaming bytes into the file.
     */
    private void recordDirectStream(String streamUrl, FileOutputStream fos) throws Exception {
        URL url = new URL(streamUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV Box) AppleWebKit/537.36");
        conn.connect();

        int responseCode = conn.getResponseCode();
        if (responseCode >= 400) {
            throw new Exception("HTTP 连接错误: " + responseCode);
        }

        try (InputStream in = conn.getInputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            long lastProgressTime = 0;

            while (isRecording.get() && (read = in.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
                totalBytesRecorded += read;

                long now = System.currentTimeMillis();
                if (now - lastProgressTime >= 1000) {
                    lastProgressTime = now;
                    notifyProgress();
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Records live HLS (.m3u8) by periodically fetching new TS segments and appending them.
     */
    private void recordHlsStream(String masterOrMediaUrl, FileOutputStream fos) throws Exception {
        String mediaPlaylistUrl = resolveMediaPlaylist(masterOrMediaUrl);
        Set<String> downloadedSegments = new HashSet<>();
        long lastProgressTime = 0;

        while (isRecording.get()) {
            List<String> segments = fetchHlsSegments(mediaPlaylistUrl);
            boolean newSegmentAdded = false;

            for (String segUrl : segments) {
                if (!isRecording.get()) break;
                if (!downloadedSegments.contains(segUrl)) {
                    downloadedSegments.add(segUrl);
                    downloadSegmentTo(segUrl, fos);
                    newSegmentAdded = true;

                    long now = System.currentTimeMillis();
                    if (now - lastProgressTime >= 1000) {
                        lastProgressTime = now;
                        notifyProgress();
                    }
                }
            }

            // Sleep 2-3 seconds before fetching next HLS playlist update
            for (int i = 0; i < 25; i++) {
                if (!isRecording.get()) break;
                Thread.sleep(100);
            }
        }
    }

    private void downloadSegmentTo(String segmentUrl, FileOutputStream fos) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(segmentUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV Box) AppleWebKit/537.36");
            conn.connect();

            if (conn.getResponseCode() < 400) {
                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[32 * 1024];
                    int read;
                    while (isRecording.get() && (read = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                        totalBytesRecorded += read;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String resolveMediaPlaylist(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV Box) AppleWebKit/537.36");
            conn.connect();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            conn.disconnect();

            String content = sb.toString();
            if (content.contains("#EXT-X-STREAM-INF")) {
                // Master playlist with multiple variants: pick highest or first
                String[] lines = content.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.startsWith("#EXT-X-STREAM-INF") && (i + 1) < lines.length) {
                        String next = lines[i + 1].trim();
                        if (!next.isEmpty() && !next.startsWith("#")) {
                            return resolveUrl(urlStr, next);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return urlStr;
    }

    private List<String> fetchHlsSegments(String mediaPlaylistUrl) {
        List<String> list = new ArrayList<>();
        try {
            URL url = new URL(mediaPlaylistUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV Box) AppleWebKit/537.36");
            conn.connect();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        list.add(resolveUrl(mediaPlaylistUrl, line));
                    }
                }
            }
            conn.disconnect();
        } catch (Exception ignored) {}
        return list;
    }

    private String resolveUrl(String baseUrl, String relativeOrAbsolute) {
        if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            return relativeOrAbsolute;
        }
        try {
            URL base = new URL(baseUrl);
            URL resolved = new URL(base, relativeOrAbsolute);
            return resolved.toString();
        } catch (Exception e) {
            return relativeOrAbsolute;
        }
    }

    private void notifyProgress() {
        final long elapsed = System.currentTimeMillis() - recordingStartTime;
        final long bytes = totalBytesRecorded;
        double rateKbps = (elapsed > 0) ? (bytes * 8.0 / elapsed) : 0;
        final String rateStr = String.format(Locale.getDefault(), "%.1f Mbps", rateKbps / 1000.0);

        mainHandler.post(() -> {
            if (activeListener != null && isRecording.get()) {
                activeListener.onRecordingProgress(elapsed, bytes, rateStr);
            }
        });
    }

    /**
     * Lists all existing recording files in the recordings directory, sorted by newest first.
     */
    public static List<RecordingItem> getRecordings(Context context) {
        List<RecordingItem> list = new ArrayList<>();
        File dir = getRecordingsDir(context);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".ts") || name.endsWith(".mp4"));
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.length() > 0) {
                    list.add(new RecordingItem(f.getName(), f.getAbsolutePath(), f.length(), f.lastModified()));
                }
            }
            Collections.sort(list, (a, b) -> Long.compare(b.lastModified, a.lastModified));
        }
        return list;
    }

    public static JSONArray getRecordingsJson(Context context) {
        JSONArray arr = new JSONArray();
        for (RecordingItem item : getRecordings(context)) {
            arr.put(item.toJson());
        }
        return arr;
    }

    public static boolean deleteRecording(Context context, String fileName) {
        File dir = getRecordingsDir(context);
        File target = new File(dir, fileName);
        if (target.exists() && target.isFile()) {
            return target.delete();
        }
        return false;
    }
}
