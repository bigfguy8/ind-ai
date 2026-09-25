package com.indai.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class AgentHistoryStore {

    private static final String PREFS = "nova_agent_history";
    private static final String KEY = "runs";
    private static final int MAX_RUNS = 30;

    private final SharedPreferences prefs;

    public AgentHistoryStore(Context c) {
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(Plan plan) {
        if (plan == null || plan.steps.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            JSONArray next = new JSONArray();
            next.put(plan.toJson());
            for (int i = 0; i < arr.length() && i < MAX_RUNS - 1; i++) {
                next.put(arr.get(i));
            }
            prefs.edit().putString(KEY, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    public List<String> rawList() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                out.add(arr.getString(i));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public void clear() {
        prefs.edit().remove(KEY).apply();
    }
}
