package com.tivilite.tv.record;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.tivilite.tv.model.Channel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TiviLite Automated Cloud DVR & NAS Archival Scheduler
 * Automates recording scheduled EPG programs directly to NAS/local storage in background.
 */
public class NasDvrSchedulerManager {
    private static final String TAG = "NasDvrScheduler";
    private static final String PREF_NAME = "tivilite_dvr_prefs";
    private static final String KEY_TASKS = "dvr_tasks_json";

    public static class DvrTask {
        public final String id;
        public final String channelName;
        public final String channelUrl;
        public final String programTitle;
        public final long startTime;
        public final long endTime;
        public String status; // PENDING, RECORDING, COMPLETED, FAILED
        public String outputPath;

        public DvrTask(String id, String channelName, String channelUrl, String programTitle, long startTime, long endTime, String status, String outputPath) {
            this.id = id;
            this.channelName = channelName;
            this.channelUrl = channelUrl;
            this.programTitle = programTitle;
            this.startTime = startTime;
            this.endTime = endTime;
            this.status = status;
            this.outputPath = outputPath;
        }

        public JSONObject toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("id", id);
                json.put("channelName", channelName);
                json.put("channelUrl", channelUrl);
                json.put("programTitle", programTitle);
                json.put("startTime", startTime);
                json.put("endTime", endTime);
                json.put("status", status);
                json.put("outputPath", outputPath != null ? outputPath : "");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                json.put("startTimeFormatted", sdf.format(new Date(startTime)));
                json.put("endTimeFormatted", sdf.format(new Date(endTime)));
                return json;
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    private static volatile NasDvrSchedulerManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, DvrTask> tasks = new ConcurrentHashMap<>();
    private DvrTask activeRecordingTask = null;

    private final Runnable dvrCheckerRunnable = new Runnable() {
        @Override
        public void run() {
            checkDvrSchedule();
            handler.postDelayed(this, 15000); // Check every 15s
        }
    };

    public static NasDvrSchedulerManager getInstance(Context context) {
        if (instance == null) {
            synchronized (NasDvrSchedulerManager.class) {
                if (instance == null) {
                    instance = new NasDvrSchedulerManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private NasDvrSchedulerManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadTasksFromPrefs();
        handler.post(dvrCheckerRunnable);
    }

    private void loadTasksFromPrefs() {
        try {
            String raw = prefs.getString(KEY_TASKS, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                DvrTask t = new DvrTask(
                        obj.optString("id", UUID.randomUUID().toString()),
                        obj.optString("channelName"),
                        obj.optString("channelUrl"),
                        obj.optString("programTitle"),
                        obj.optLong("startTime"),
                        obj.optLong("endTime"),
                        obj.optString("status", "PENDING"),
                        obj.optString("outputPath")
                );
                tasks.put(t.id, t);
            }
        } catch (Exception ignored) {}
    }

    private void saveTasksToPrefs() {
        try {
            JSONArray arr = new JSONArray();
            for (DvrTask t : tasks.values()) {
                arr.put(t.toJson());
            }
            prefs.edit().putString(KEY_TASKS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public DvrTask scheduleRecording(String channelName, String channelUrl, String programTitle, long startTime, long endTime) {
        String id = UUID.randomUUID().toString();
        DvrTask task = new DvrTask(id, channelName, channelUrl, programTitle, startTime, endTime, "PENDING", null);
        tasks.put(id, task);
        saveTasksToPrefs();
        Log.i(TAG, "Scheduled DVR task: " + programTitle + " on " + channelName);
        return task;
    }

    public boolean cancelTask(String taskId) {
        if (tasks.containsKey(taskId)) {
            DvrTask t = tasks.get(taskId);
            if ("RECORDING".equals(t.status)) {
                RecordingManager.getInstance().stopRecording();
                activeRecordingTask = null;
            }
            tasks.remove(taskId);
            saveTasksToPrefs();
            return true;
        }
        return false;
    }

    public List<DvrTask> getAllTasks() {
        return new ArrayList<>(tasks.values());
    }

    private void checkDvrSchedule() {
        long now = System.currentTimeMillis();

        for (DvrTask task : tasks.values()) {
            if ("PENDING".equals(task.status)) {
                // If within 30 seconds of start time
                if (now >= task.startTime - 30000 && now < task.endTime) {
                    startRecordingTask(task);
                } else if (now > task.endTime) {
                    task.status = "EXPIRED";
                    saveTasksToPrefs();
                }
            } else if ("RECORDING".equals(task.status)) {
                // Check if time is up
                if (now >= task.endTime) {
                    stopRecordingTask(task);
                }
            }
        }
    }

    private void startRecordingTask(DvrTask task) {
        if (activeRecordingTask != null) return; // Only 1 active task for stability

        activeRecordingTask = task;
        task.status = "RECORDING";

        Channel ch = new Channel(0, task.channelName, "DVR", task.channelUrl);
        RecordingManager.getInstance().startRecording(context, ch, task.channelUrl, new RecordingManager.RecordingListener() {
            @Override
            public void onRecordingStarted(File outputFile, Channel channel) {
                task.outputPath = outputFile.getAbsolutePath();
                saveTasksToPrefs();
                Log.i(TAG, "Started background DVR recording to: " + task.outputPath);
            }

            @Override
            public void onRecordingProgress(long bytesWritten, long durationMs) {
            }

            @Override
            public void onRecordingStopped(File outputFile, Channel channel, long totalBytes, long durationMs) {
                task.status = "COMPLETED";
                task.outputPath = outputFile.getAbsolutePath();
                activeRecordingTask = null;
                saveTasksToPrefs();
                Log.i(TAG, "Completed background DVR recording: " + task.programTitle);
            }

            @Override
            public void onRecordingError(String errorMessage) {
                task.status = "FAILED: " + errorMessage;
                activeRecordingTask = null;
                saveTasksToPrefs();
                Log.e(TAG, "DVR recording error: " + errorMessage);
            }
        });
    }

    private void stopRecordingTask(DvrTask task) {
        RecordingManager.getInstance().stopRecording();
        task.status = "COMPLETED";
        activeRecordingTask = null;
        saveTasksToPrefs();
        Log.i(TAG, "Completed background DVR recording: " + task.programTitle);
    }

    public JSONObject getStatusJson() {
        JSONObject json = new JSONObject();
        try {
            JSONArray arr = new JSONArray();
            for (DvrTask t : tasks.values()) {
                arr.put(t.toJson());
            }
            json.put("tasks", arr);
            json.put("taskCount", arr.length());
            json.put("isRecordingNow", activeRecordingTask != null);
            if (activeRecordingTask != null) {
                json.put("activeProgram", activeRecordingTask.programTitle);
                json.put("activeChannel", activeRecordingTask.channelName);
            }
        } catch (Exception ignored) {}
        return json;
    }
}
