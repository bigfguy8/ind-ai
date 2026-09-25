package com.indai.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Persistent storage for Goal objects. Backed by SharedPreferences
 * (matches existing ChatStore / MemoryStore pattern).
 *
 * Thread-safe — every public method is synchronized.
 */
public class GoalStore {

    private static final String PREFS = "indai_goals";
    private static final String KEY = "goals";
    private static final int MAX_GOALS = 100;

    private final SharedPreferences prefs;

    public GoalStore(Context c) {
        this.prefs = c.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void save(Goal goal) {
        if (goal == null) return;
        List<Goal> all = readAll();
        all.add(goal);
        trim(all);
        persist(all);
    }

    public synchronized void update(Goal goal) {
        if (goal == null) return;
        goal.updatedAt = System.currentTimeMillis();
        List<Goal> all = readAll();
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(goal.id)) {
                all.set(i, goal);
                found = true;
                break;
            }
        }
        if (!found) all.add(goal);
        trim(all);
        persist(all);
    }

    public synchronized Goal get(String id) {
        if (id == null) return null;
        for (Goal g : readAll()) {
            if (g.id.equals(id)) return g;
        }
        return null;
    }

    public synchronized List<Goal> getAll() {
        List<Goal> all = readAll();
        sortByUpdatedDesc(all);
        return all;
    }

    public synchronized List<Goal> getActiveGoals() {
        List<Goal> out = new ArrayList<>();
        for (Goal g : readAll()) {
            if (g.isLive()) out.add(g);
        }
        sortByUpdatedDesc(out);
        return out;
    }

    public synchronized int countLive() {
        int n = 0;
        for (Goal g : readAll()) {
            if (g.isLive()) n++;
        }
        return n;
    }

    public synchronized void delete(String id) {
        if (id == null) return;
        List<Goal> all = readAll();
        List<Goal> kept = new ArrayList<>();
        for (Goal g : all) {
            if (!g.id.equals(id)) kept.add(g);
        }
        persist(kept);
    }

    public synchronized void clearCompleted() {
        List<Goal> kept = new ArrayList<>();
        for (Goal g : readAll()) {
            if (g.status != Goal.Status.COMPLETED) kept.add(g);
        }
        persist(kept);
    }

    public synchronized void clearAll() {
        prefs.edit().remove(KEY).apply();
    }

    // ------------------------------------------------------------------

    private List<Goal> readAll() {
        List<Goal> out = new ArrayList<>();
        try {
            String raw = prefs.getString(KEY, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                Goal g = Goal.fromJson(arr.optJSONObject(i));
                if (g != null) out.add(g);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void persist(List<Goal> goals) {
        try {
            JSONArray arr = new JSONArray();
            for (Goal g : goals) arr.put(g.toJson());
            prefs.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void trim(List<Goal> goals) {
        // Keep all live goals; cap total to MAX_GOALS by dropping oldest completed/failed first
        if (goals.size() <= MAX_GOALS) return;
        Collections.sort(goals, new Comparator<Goal>() {
            @Override public int compare(Goal a, Goal b) {
                // live goals first, then by updated desc
                boolean la = a.isLive();
                boolean lb = b.isLive();
                if (la != lb) return la ? -1 : 1;
                return Long.compare(b.updatedAt, a.updatedAt);
            }
        });
        while (goals.size() > MAX_GOALS) {
            goals.remove(goals.size() - 1);
        }
    }

    private void sortByUpdatedDesc(List<Goal> goals) {
        Collections.sort(goals, new Comparator<Goal>() {
            @Override public int compare(Goal a, Goal b) {
                return Long.compare(b.updatedAt, a.updatedAt);
            }
        });
    }
}
