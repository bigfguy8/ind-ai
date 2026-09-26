package com.indai.assistant;

/**
 * Enforces per-goal budgets to prevent runaway LLM/tool usage.
 * Conservative defaults match a free-tier provider.
 */
public class BudgetManager {

    public static final int DEFAULT_MAX_LLM_CALLS = 8;
    public static final int DEFAULT_MAX_TOOL_CALLS = 25;
    public static final int DEFAULT_MAX_PLAN_STEPS = 10;
    public static final int DEFAULT_MAX_REPLANS = 2;
    public static final int DEFAULT_MAX_RETRIES = 1;
    public static final long DEFAULT_MAX_WALL_MS = 180_000L; // 3 minutes

    private final int maxLlmCalls;
    private final int maxToolCalls;
    private final int maxPlanSteps;
    private final int maxReplans;
    private final int maxRetries;
    private final long maxWallMs;

    private final long startedAt;
    private int llmCalls = 0;
    private int toolCalls = 0;
    private int planSteps = 0;
    private int replans = 0;
    private int retries = 0;
    private boolean killed = false;
    private String killReason = "";

    public BudgetManager() {
        this(DEFAULT_MAX_LLM_CALLS, DEFAULT_MAX_TOOL_CALLS, DEFAULT_MAX_PLAN_STEPS,
             DEFAULT_MAX_REPLANS, DEFAULT_MAX_RETRIES, DEFAULT_MAX_WALL_MS);
    }

    public BudgetManager(int maxLlmCalls, int maxToolCalls, int maxPlanSteps,
                         int maxReplans, int maxRetries, long maxWallMs) {
        this.maxLlmCalls = maxLlmCalls;
        this.maxToolCalls = maxToolCalls;
        this.maxPlanSteps = maxPlanSteps;
        this.maxReplans = maxReplans;
        this.maxRetries = maxRetries;
        this.maxWallMs = maxWallMs;
        this.startedAt = System.currentTimeMillis();
    }

    public synchronized boolean recordLlmCall() {
        if (killed) return false;
        if (++llmCalls > maxLlmCalls) {
            killed = true;
            killReason = "LLM call budget exceeded (" + maxLlmCalls + ")";
            return false;
        }
        return true;
    }

    public synchronized boolean recordToolCall() {
        if (killed) return false;
        if (++toolCalls > maxToolCalls) {
            killed = true;
            killReason = "Tool call budget exceeded (" + maxToolCalls + ")";
            return false;
        }
        return true;
    }

    public synchronized boolean recordPlanStep() {
        if (killed) return false;
        if (++planSteps > maxPlanSteps) {
            killed = true;
            killReason = "Plan step budget exceeded (" + maxPlanSteps + ")";
            return false;
        }
        return true;
    }

    public synchronized boolean recordReplan() {
        if (killed) return false;
        if (++replans > maxReplans) {
            killed = true;
            killReason = "Replan budget exceeded (" + maxReplans + ")";
            return false;
        }
        return true;
    }

    public synchronized boolean recordRetry() {
        if (killed) return false;
        if (++retries > maxRetries) {
            killed = true;
            killReason = "Retry budget exceeded (" + maxRetries + ")";
            return false;
        }
        return true;
    }

    public synchronized boolean isExhausted() {
        if (killed) return true;
        if (System.currentTimeMillis() - startedAt > maxWallMs) {
            killed = true;
            killReason = "Time budget exceeded (" + (maxWallMs / 1000) + "s)";
            return true;
        }
        return false;
    }

    public synchronized boolean isKilled() { return killed; }
    public synchronized String killReason() { return killReason; }

    public synchronized int getLlmCalls() { return llmCalls; }
    public synchronized int getToolCalls() { return toolCalls; }

    public synchronized String summary() {
        return llmCalls + " LLM · " + toolCalls + " tools · " + planSteps + " steps"
                + (killed ? " · " + killReason : "");
    }
}
