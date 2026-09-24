package com.nova.assistant;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ReminderScheduler {

    private static final String PREFS = "nova_reminders";
    private static final String KEY = "items";

    public static class Item {
        public final int id;
        public final long atMillis;
        public final String text;
        public Item(int id, long at, String text) {
            this.id = id; this.atMillis = at; this.text = text;
        }
    }

    public static int schedule(Context c, long atMillis, String text) {
        int id = nextId(c);
        Intent i = new Intent(c, ReminderReceiver.class);
        i.putExtra("id", id);
        i.putExtra("text", text);
        PendingIntent pi = PendingIntent.getBroadcast(
                c, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return -1;

        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, atMillis, pi);
        }

        Item item = new Item(id, atMillis, text);
        save(c, item);
        return id;
    }

    public static void cancel(Context c, int id) {
        Intent i = new Intent(c, ReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                c, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi);
        remove(c, id);
    }

    public static List<Item> list(Context c) {
        List<Item> out = new ArrayList<>();
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(p.getString(KEY, "[]"));
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                long at = o.getLong("at");
                if (at < now - 60_000) continue;
                out.add(new Item(o.getInt("id"), at, o.getString("text")));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void clearPast(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(p.getString(KEY, "[]"));
            JSONArray kept = new JSONArray();
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.getLong("at") >= now) kept.put(o);
            }
            p.edit().putString(KEY, kept.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static void save(Context c, Item item) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(p.getString(KEY, "[]"));
            JSONObject o = new JSONObject();
            o.put("id", item.id);
            o.put("at", item.atMillis);
            o.put("text", item.text);
            arr.put(o);
            p.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static void remove(Context c, int id) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(p.getString(KEY, "[]"));
            JSONArray kept = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.getInt("id") != id) kept.put(o);
            }
            p.edit().putString(KEY, kept.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static int nextId(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int n = p.getInt("next_id", 1000);
        p.edit().putInt("next_id", n + 1).apply();
        return n;
    }

    private ReminderScheduler() {}
}
