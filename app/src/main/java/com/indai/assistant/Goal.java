package com.indai.assistant;

import org.json.JSONObject;

import java.util.UUID;

public class Goal {

    public enum Status {
        ACTIVE, PAUSED, BLOCKED, COMPLETED, FAILED
    }

    public String id;
    public String title;
    public String description;
    public Status status;
    public int priority;      // 1 = high, 2 = normal, 3 = low
    public long createdAt;
    public long updatedAt;
    public int currentStep;
    public int totalSteps;
    public String planJson;
    public String lastError;
    public int retryCount;
    public String nextAction;
    public String metadataJson;

    public Goal(String title, String description) {
        this.id = UUID.randomUUID().toString();
        this.title = title == null ? "Untitled" : title;
        this.description = description == null ? "" : description;
        this.status = Status.ACTIVE;
        this.priority = 2;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.currentStep = 0;
        this.totalSteps = 0;
        this.planJson = "";
        this.lastError = "";
        this.retryCount = 0;
        this.nextAction = "";
        this.metadataJson = "{}";
    }

    public String progressLabel() {
        if (totalSteps == 0) return "Not planned";
        return currentStep + "/" + totalSteps + " steps";
    }

    public String statusLabel() {
        switch (status) {
            case ACTIVE: return "Active";
            case PAUSED: return "Paused";
            case BLOCKED: return "Blocked";
            case COMPLETED: return "Done";
            case FAILED: return "Failed";
            default: return "";
        }
    }

    public boolean isLive() {
        return status == Status.ACTIVE || status == Status.BLOCKED;
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title);
            o.put("description", description);
            o.put("status", status == null ? "ACTIVE" : status.name());
            o.put("priority", priority);
            o.put("createdAt", createdAt);
            o.put("updatedAt", updatedAt);
            o.put("currentStep", currentStep);
            o.put("totalSteps", totalSteps);
            o.put("planJson", planJson == null ? "" : planJson);
            o.put("lastError", lastError == null ? "" : lastError);
            o.put("retryCount", retryCount);
            o.put("nextAction", nextAction == null ? "" : nextAction);
            o.put("metadataJson", metadataJson == null ? "{}" : metadataJson);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static Goal fromJson(JSONObject o) {
        if (o == null) return null;
        try {
            Goal g = new Goal(o.optString("title", "Untitled"),
                    o.optString("description", ""));
            g.id = o.optString("id", g.id);
            try {
                g.status = Status.valueOf(o.optString("status", "ACTIVE"));
            } catch (Exception e) {
                g.status = Status.ACTIVE;
            }
            g.priority = o.optInt("priority", 2);
            g.createdAt = o.optLong("createdAt", g.createdAt);
            g.updatedAt = o.optLong("updatedAt", g.updatedAt);
            g.currentStep = o.optInt("currentStep", 0);
            g.totalSteps = o.optInt("totalSteps", 0);
            g.planJson = o.optString("planJson", "");
            g.lastError = o.optString("lastError", "");
            g.retryCount = o.optInt("retryCount", 0);
            g.nextAction = o.optString("nextAction", "");
            g.metadataJson = o.optString("metadataJson", "{}");
            return g;
        } catch (Exception e) {
            return null;
        }
    }
}
