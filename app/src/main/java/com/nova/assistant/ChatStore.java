package com.nova.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

public class ChatStore {

    private static final String PREFS = "nova_chat";
    private static final String KEY = "history";

    private final SharedPreferences prefs;

    public ChatStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(JSONArray history) {
        prefs.edit().putString(KEY, history.toString()).apply();
    }

    public JSONArray load() {
        String raw = prefs.getString(KEY, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public void clear() {
        prefs.edit().remove(KEY).apply();
    }
}
