package com.nova.assistant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Plan {

    public enum StepStatus { PENDING, RUNNING, DONE, FAILED, SKIPPED, RETRYING }
    public enum OverallStatus { PLANNING, RUNNING, REVISING, DONE, FAILED, CANCELLED }

    public static class Step {
        public final int index;
        public String description;
        public String toolHint;
        public StepStatus status = StepStatus.PENDING;
        public String result = "";
        public int attempts = 0;
        public long startedAt = 0;
        public long finishedAt = 0;

        public Step(int index, String description, String toolHint) {
            this.index = index;
            this.description = description;
            this.toolHint = toolHint;
        }
    }

    public final String id;
    public final String goal;
    public final List<Step> steps;
    public final long createdAt;
    public OverallStatus status = OverallStatus.PLANNING;
    public int revision = 0;

    public Plan(String goal, List<Step> steps) {
        this.id = UUID.randomUUID().toString();
        this.goal = goal;
        this.steps = steps;
        this.createdAt = System.currentTimeMillis();
    }

    public int size() { return steps.size(); }

    public Step current() {
        for (Step s : steps) if (s.status == StepStatus.PENDING) return s;
        return null;
    }

    public int completedCount() {
        int n = 0;
        for (Step s : steps) if (s.status == StepStatus.DONE) n++;
        return n;
    }

    public int failedCount() {
        int n = 0;
        for (Step s : steps) if (s.status == StepStatus.FAILED) n++;
        return n;
    }

    /**
     * Replace all steps from `fromIndex` onward with `newSteps`.
     * Steps before fromIndex keep their state. Indices are renumbered.
     */
    public void replaceRemainingFrom(int fromIndex, List<Step> newSteps) {
        if (fromIndex < 0) fromIndex = 0;
        if (fromIndex > steps.size()) fromIndex = steps.size();
        // Keep steps [0, fromIndex)
        List<Step> kept = new ArrayList<>(steps.subList(0, fromIndex));
        // Renumber new steps
        int idx = kept.size();
        for (Step s : newSteps) {
            Step ns = new Step(idx++, s.description, s.toolHint);
            kept.add(ns);
        }
        steps.clear();
        steps.addAll(kept);
        revision++;
    }

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("goal", goal);
            o.put("status", status.name());
            o.put("revision", revision);
            o.put("created_at", createdAt);
            JSONArray arr = new JSONArray();
            for (Step s : steps) {
                JSONObject so = new JSONObject();
                so.put("index", s.index);
                so.put("description", s.description);
                so.put("tool", s.toolHint == null ? JSONObject.NULL : s.toolHint);
                so.put("status", s.status.name());
                so.put("result", s.result);
                so.put("attempts", s.attempts);
                so.put("started_at", s.startedAt);
                so.put("finished_at", s.finishedAt);
                arr.put(so);
            }
            o.put("steps", arr);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static Plan fromJson(String raw) {
        try {
            JSONObject o = new JSONObject(raw);
            String goal = o.optString("goal", "");
            List<Step> steps = new ArrayList<>();
            JSONArray arr = o.optJSONArray("steps");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject so = arr.getJSONObject(i);
                    Step s = new Step(i, so.optString("description", ""),
                            so.isNull("tool") ? null : so.optString("tool", null));
                    try {
                        s.status = StepStatus.valueOf(so.optString("status", "PENDING"));
                    } catch (Exception ignored) {}
                    s.result = so.optString("result", "");
                    s.attempts = so.optInt("attempts", 0);
                    s.startedAt = so.optLong("started_at", 0);
                    s.finishedAt = so.optLong("finished_at", 0);
                    steps.add(s);
                }
            }
            Plan p = new Plan(goal, steps);
            try {
                p.status = OverallStatus.valueOf(o.optString("status", "PLANNING"));
            } catch (Exception ignored) {}
            p.revision = o.optInt("revision", 0);
            return p;
        } catch (Exception e) {
            return null;
        }
    }
}
