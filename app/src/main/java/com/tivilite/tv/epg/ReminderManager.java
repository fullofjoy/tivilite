package com.tivilite.tv.epg;

import android.content.Context;
import android.content.SharedPreferences;
import com.tivilite.tv.model.Program;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class ReminderManager {

    public static class ReminderItem implements Comparable<ReminderItem> {
        public final String id;
        public final String channelName;
        public final String channelUrl;
        public final String programTitle;
        public final long startTime;
        public final long endTime;
        public boolean reminded;

        public ReminderItem(String id, String channelName, String channelUrl, String programTitle, long startTime, long endTime, boolean reminded) {
            this.id = id;
            this.channelName = channelName;
            this.channelUrl = channelUrl;
            this.programTitle = programTitle;
            this.startTime = startTime;
            this.endTime = endTime;
            this.reminded = reminded;
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
                json.put("reminded", reminded);
                return json;
            } catch (Exception e) {
                return null;
            }
        }

        public static ReminderItem fromJson(JSONObject json) {
            if (json == null) return null;
            String id = json.optString("id");
            String channelName = json.optString("channelName");
            String channelUrl = json.optString("channelUrl", "");
            String programTitle = json.optString("programTitle");
            long startTime = json.optLong("startTime");
            long endTime = json.optLong("endTime");
            boolean reminded = json.optBoolean("reminded", false);
            return new ReminderItem(id, channelName, channelUrl, programTitle, startTime, endTime, reminded);
        }

        @Override
        public int compareTo(ReminderItem o) {
            return Long.compare(this.startTime, o.startTime);
        }
    }

    private static ReminderManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final List<ReminderItem> cachedReminders = new ArrayList<>();

    private ReminderManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences("tivilite_reminders_prefs", Context.MODE_PRIVATE);
        loadReminders();
    }

    public static synchronized ReminderManager getInstance(Context context) {
        if (instance == null) {
            instance = new ReminderManager(context);
        }
        return instance;
    }

    private synchronized void loadReminders() {
        cachedReminders.clear();
        String jsonStr = prefs.getString("reminders_json", "[]");
        try {
            JSONArray arr = new JSONArray(jsonStr);
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                ReminderItem item = ReminderItem.fromJson(arr.getJSONObject(i));
                if (item != null) {
                    // 仅保留未过期超过 12 小时的预约
                    if (item.endTime > 0 && now - item.endTime > 12 * 3600 * 1000L) {
                        continue;
                    }
                    cachedReminders.add(item);
                }
            }
            Collections.sort(cachedReminders);
        } catch (Exception ignored) {
        }
    }

    private synchronized void saveReminders() {
        try {
            JSONArray arr = new JSONArray();
            for (ReminderItem item : cachedReminders) {
                JSONObject json = item.toJson();
                if (json != null) arr.put(json);
            }
            prefs.edit().putString("reminders_json", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public synchronized boolean isReminderSet(String channelName, String programTitle, long startTime) {
        if (channelName == null || programTitle == null) return false;
        for (ReminderItem item : cachedReminders) {
            if (channelName.equalsIgnoreCase(item.channelName) &&
                programTitle.equalsIgnoreCase(item.programTitle) &&
                Math.abs(startTime - item.startTime) < 60_000L) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean toggleReminder(String channelName, String channelUrl, Program program) {
        if (channelName == null || program == null) return false;
        if (isReminderSet(channelName, program.getTitle(), program.getStartTime())) {
            removeReminder(channelName, program.getTitle(), program.getStartTime());
            return false;
        } else {
            addReminder(channelName, channelUrl, program.getTitle(), program.getStartTime(), program.getEndTime());
            return true;
        }
    }

    public synchronized void addReminder(String channelName, String channelUrl, String programTitle, long startTime, long endTime) {
        String id = channelName + "_" + startTime;
        for (ReminderItem existing : cachedReminders) {
            if (existing.id.equals(id)) return;
        }
        ReminderItem item = new ReminderItem(id, channelName, channelUrl, programTitle, startTime, endTime, false);
        cachedReminders.add(item);
        Collections.sort(cachedReminders);
        saveReminders();
    }

    public synchronized void removeReminder(String channelName, String programTitle, long startTime) {
        ReminderItem toRemove = null;
        for (ReminderItem item : cachedReminders) {
            if (channelName.equalsIgnoreCase(item.channelName) &&
                programTitle.equalsIgnoreCase(item.programTitle) &&
                Math.abs(startTime - item.startTime) < 60_000L) {
                toRemove = item;
                break;
            }
        }
        if (toRemove != null) {
            cachedReminders.remove(toRemove);
            saveReminders();
        }
    }

    public synchronized void removeReminderById(String id) {
        ReminderItem toRemove = null;
        for (ReminderItem item : cachedReminders) {
            if (item.id.equals(id)) {
                toRemove = item;
                break;
            }
        }
        if (toRemove != null) {
            cachedReminders.remove(toRemove);
            saveReminders();
        }
    }

    public synchronized List<ReminderItem> getAllReminders() {
        return new ArrayList<>(cachedReminders);
    }

    public synchronized int getUpcomingCount() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (ReminderItem item : cachedReminders) {
            if (item.startTime > now) {
                count++;
            }
        }
        return count;
    }

    /**
     * 周期性探测是否有即将开播的预约节目（开播前 90 秒至开播后 3 分钟内）
     */
    public synchronized List<ReminderItem> pollPendingAlerts() {
        long now = System.currentTimeMillis();
        List<ReminderItem> alerts = new ArrayList<>();
        boolean modified = false;

        for (ReminderItem item : cachedReminders) {
            if (!item.reminded) {
                // 开播前 90 秒至开播后 180 秒内
                if (now >= (item.startTime - 90_000L) && now <= (item.startTime + 180_000L)) {
                    item.reminded = true;
                    alerts.add(item);
                    modified = true;
                }
            }
        }

        if (modified) {
            saveReminders();
        }
        return alerts;
    }
}
