package com.nova.assistant;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AgentSession {

    public interface Listener {
        void onPlanning();
        void onPlanReady(Plan plan);
        void onStepStarted(Plan plan, int index);
        void onStepNote(Plan plan, int index, String note);
        void onStepFinished(Plan plan, int index, boolean success, String result);
        void onStepRetrying(Plan plan, int index);
        void onReplanning(Plan plan, int afterIndex, String reason);
        void onPlanRevised(Plan plan);
        void onReport(String summary);
        void onError(String reason);
        void onFinished(Plan plan);
    }

    private final Context ctx;
    private final ApiConfig config;
    private final MemoryStore memoryStore;
    private final Listener listener;

    private volatile boolean cancelled = false;
    private volatile Plan current;

    public AgentSession(Context ctx, ApiConfig config,
                        MemoryStore memoryStore, Listener listener) {
        this.ctx = ctx;
        this.config = config;
        this.memoryStore = memoryStore;
        this.listener = listener;
    }

    public Plan currentPlan() { return current; }

    public void cancel() { cancelled = true; }

    public void start(final String goal) {
        cancelled = false;
        listener.onPlanning();

        String memBlock = memoryStore != null ? memoryStore.asSystemBlock() : "";

        Planner.plan(config, goal, memBlock, new Planner.Callback() {
            @Override public void onPlan(final Plan plan) {
                current = plan;
                listener.onPlanReady(plan);

                Executor.run(ctx, config, plan, new Executor.Listener() {
                    @Override public void onStepStarted(int index) {
                        listener.onStepStarted(plan, index);
                    }

                    @Override public void onStepNote(int index, String note) {
                        listener.onStepNote(plan, index, note);
                    }

                    @Override public void onStepFinished(int index, boolean success, String result) {
                        listener.onStepFinished(plan, index, success, result);
                    }

                    @Override public void onStepRetrying(int index) {
                        listener.onStepRetrying(plan, index);
                    }

                    @Override public void onReplanning(int afterIndex, String reason) {
                        listener.onReplanning(plan, afterIndex, reason);
                    }

                    @Override public void onPlanRevised(Plan revised) {
                        // plan object is mutated in place by Executor, so same reference
                        listener.onPlanRevised(revised);
                    }

                    @Override public void onPlanDone(Plan done) {
                        report(done);
                    }

                    @Override public boolean isCancelled() {
                        return cancelled;
                    }
                });
            }

            @Override public void onError(String reason) {
                listener.onError(reason);
                listener.onFinished(null);
            }
        });
    }

    private void report(final Plan plan) {
        if (cancelled && plan.status == Plan.OverallStatus.CANCELLED) {
            listener.onReport("Cancelled by user after "
                    + plan.completedCount() + " of " + plan.size() + " steps.");
            listener.onFinished(plan);
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                String summary = buildSummary(plan);
                listener.onReport(summary);
                listener.onFinished(plan);
            }
        }).start();
    }

    private String buildSummary(Plan plan) {
        if (config.apiKey == null || config.apiKey.isEmpty()) return localSummary(plan);

        HttpURLConnection conn = null;
        try {
            JSONArray messages = new JSONArray();
            put(messages, "system",
                "You are summarizing an autonomous agent run for the user. " +
                "Write a plain-language report of 2 to 5 sentences. " +
                "Say what was accomplished, what failed (if anything), and what remains. " +
                "If the plan was revised mid-run, mention that. " +
                "Do not use markdown. Do not invent results. Use only the facts given.");
            put(messages, "user", transcript(plan));

            JSONObject body = new JSONObject();
            body.put("model", config.model);
            body.put("temperature", 0.3);
            body.put("max_tokens", 400);
            body.put("messages", messages);

            URL url = new URL(config.endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            if (config.apiKey != null && !config.apiKey.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + config.apiKey);
            }

            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream out = conn.getOutputStream();
            out.write(data);
            out.flush();
            out.close();

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) return localSummary(plan);

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            String content = new JSONObject(sb.toString())
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();
            return content.isEmpty() ? localSummary(plan) : content;
        } catch (Exception e) {
            return localSummary(plan);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String transcript(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("Goal: ").append(plan.goal).append("\n");
        if (plan.revision > 0) {
            sb.append("Plan revisions: ").append(plan.revision).append("\n");
        }
        sb.append("\nSteps:\n");
        for (Plan.Step s : plan.steps) {
            sb.append(s.index + 1).append(". ").append(s.description)
              .append("  [").append(s.status.name()).append("]");
            if (s.attempts > 0) sb.append(" attempts=").append(s.attempts);
            if (s.result != null && !s.result.isEmpty()) {
                sb.append("\n   Result: ").append(s.result);
            }
            sb.append("\n");
        }
        sb.append("\nOutcome: ")
          .append(plan.status.name())
          .append(" — ").append(plan.completedCount())
          .append("/").append(plan.size()).append(" completed, ")
          .append(plan.failedCount()).append(" failed.");
        return sb.toString();
    }

    private static String localSummary(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("Goal: ").append(plan.goal).append(". ");
        sb.append(plan.completedCount()).append(" of ").append(plan.size())
          .append(" steps completed");
        if (plan.failedCount() > 0) {
            sb.append(", ").append(plan.failedCount()).append(" failed");
        }
        if (plan.revision > 0) {
            sb.append(", plan revised ").append(plan.revision).append(" time")
              .append(plan.revision == 1 ? "" : "s");
        }
        sb.append(". ");
        if (plan.status == Plan.OverallStatus.CANCELLED) sb.append("Cancelled by user. ");
        if (plan.status == Plan.OverallStatus.DONE) sb.append("Finished successfully.");
        return sb.toString();
    }

    private static void put(JSONArray arr, String role, String content) {
        try {
            JSONObject o = new JSONObject();
            o.put("role", role);
            o.put("content", content);
            arr.put(o);
        } catch (Exception ignored) {}
    }
}
