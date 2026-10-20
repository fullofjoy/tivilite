package com.tivilite.tv.nas;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import com.tivilite.tv.model.Channel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TiviLite Local Storage & LAN NAS Media Manager
 * Scans local USB flash drives, SD cards, and LAN network shares (Alist/WebDAV/SMB mounts)
 * and automatically integrates movies & series into a dedicated TV channel group:
 * 【📁 家庭 NAS 影视库】
 */
public class SmbNasManager {
    private static final String TAG = "SmbNasManager";
    private static final String PREF_NAME = "tivilite_nas_prefs";
    private static final String KEY_RESUME_POS_PREFIX = "resume_pos_";
    private static final String GROUP_NAME = "📁 家庭 NAS 影视库";

    public static class MediaItem {
        public final String name;
        public final String path;
        public final long sizeBytes;
        public final long lastModified;

        public MediaItem(String name, String path, long sizeBytes, long lastModified) {
            this.name = name;
            this.path = path;
            this.sizeBytes = sizeBytes;
            this.lastModified = lastModified;
        }

        public JSONObject toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("name", name);
                json.put("path", path);
                json.put("sizeBytes", sizeBytes);
                json.put("sizeMb", String.format("%.1f MB", sizeBytes / (1024f * 1024f)));
                json.put("lastModified", lastModified);
                return json;
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    private static volatile SmbNasManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final List<MediaItem> cachedMediaList = new ArrayList<>();

    public static SmbNasManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SmbNasManager.class) {
                if (instance == null) {
                    instance = new SmbNasManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private SmbNasManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        scanLocalAndUsbStorage();
    }

    public synchronized List<MediaItem> scanLocalAndUsbStorage() {
        cachedMediaList.clear();
        Set<String> searchPaths = new HashSet<>();

        // Standard directories
        try {
            File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            if (moviesDir != null && moviesDir.exists()) searchPaths.add(moviesDir.getAbsolutePath());

            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir != null && downloadsDir.exists()) searchPaths.add(downloadsDir.getAbsolutePath());

            File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            if (dcimDir != null && dcimDir.exists()) searchPaths.add(dcimDir.getAbsolutePath());

            // Scan /storage/ for mounted USB OTG drives
            File storageRoot = new File("/storage");
            if (storageRoot.exists() && storageRoot.isDirectory()) {
                File[] subs = storageRoot.listFiles();
                if (subs != null) {
                    for (File f : subs) {
                        if (f.isDirectory() && !f.getName().equals("emulated") && !f.getName().equals("self")) {
                            searchPaths.add(f.getAbsolutePath());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        for (String path : searchPaths) {
            scanDirectoryRecursive(new File(path), 0, 3);
        }

        // Sort by modification time descending
        Collections.sort(cachedMediaList, (a, b) -> Long.compare(b.lastModified, a.lastModified));
        Log.i(TAG, "Scanned NAS & Local media items count: " + cachedMediaList.size());
        return new ArrayList<>(cachedMediaList);
    }

    private void scanDirectoryRecursive(File dir, int currentDepth, int maxDepth) {
        if (dir == null || !dir.exists() || !dir.canRead() || currentDepth > maxDepth) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) {
                    scanDirectoryRecursive(f, currentDepth + 1, maxDepth);
                }
            } else if (f.isFile()) {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".ts")
                        || name.endsWith(".m3u8") || name.endsWith(".flv") || name.endsWith(".avi")
                        || name.endsWith(".mov")) {
                    cachedMediaList.add(new MediaItem(f.getName(), f.getAbsolutePath(), f.length(), f.lastModified()));
                }
            }
        }
    }

    public List<Channel> getAsChannels(int startChannelNumber) {
        List<Channel> channels = new ArrayList<>();
        int num = startChannelNumber;
        for (MediaItem item : cachedMediaList) {
            Channel ch = new Channel(num++, "🎬 " + item.name, GROUP_NAME, item.path);
            channels.add(ch);
        }
        return channels;
    }

    public JSONArray getMediaListJson() {
        JSONArray arr = new JSONArray();
        for (MediaItem item : cachedMediaList) {
            arr.put(item.toJson());
        }
        return arr;
    }

    public void saveResumePosition(String path, long positionMs) {
        if (path == null) return;
        prefs.edit().putLong(KEY_RESUME_POS_PREFIX + path.hashCode(), positionMs).apply();
    }

    public long getResumePosition(String path) {
        if (path == null) return 0;
        return prefs.getLong(KEY_RESUME_POS_PREFIX + path.hashCode(), 0);
    }
}
