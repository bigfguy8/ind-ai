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

public final class Executor {

    public interface Listener {
        void onStepStarted(int index);
        void onStepNote(int index, String note);
        void onStepFinished(int index, boolean success, String result);
        void onStepRetrying(int index);
        void onReplanning(int afterIndex, String reason);
        void onPlanRevised(Plan plan);
        void onPlanDone(Plan plan);
        boolean isCancelled();
    }

    private static final int MAX_ITER_PER_STEP = 3;
    private static final int TOOL_TIMEOUT_MS = 8000;
    private static final int MAX_STEP_RETRIES = 1;
    private static final int MAX_REPLANS = 2;

    private static final String STEP_SYSTEM =
        "You are the execution module of an AI agent running on an Android phone. " +
        "You are given ONE step of a plan. Carry it out.\n" +
        "\n" +
        "Rules:\n" +
        "- If the step needs a device action, emit exactly one tool call in this form:\n" +
        "  <tool_call>{\"name\":\"open_app\",\"arguments\":{\"package\":\"com.android.settings\"}}</tool_call>\n" +
        "- Available tools: open_app, open_url, click_text, click_description, " +
        "type_text, scroll, press_back, press_home, read_screen, see_screen, " +
        "list_tappable, click_at, set_reminder, list_reminders, cancel_reminder.\n" +
        "- If the step is informational, just answer it directly in one or two sentences.\n" +
        "- After a tool result comes back, reply with ONE sentence summarizing what happened.\n" +
        "- If a step cannot be done, reply: DONE: not possible — <short reason>.\n" +
        "- Never invent tools. Never output shell commands.\n" +
        "- Be concise. The user sees your notes live.";

    public static void run(final Context ctx, final ApiConfig config,
                           final Plan plan, final Listener listener) {
        plan.status = Plan.OverallStatus.RUNNING;
        new Thread(new Runnable() {
            @Override public void run() {
                int replanCount = 0;
                int i = 0;
                while (i < plan.steps.size()) {
                    if (listener.isCancelled()) {
                        plan.status = Plan.OverallStatus.CANCELLED;
                        for (Plan.Step s : plan.steps) {
                            if (s.status == Plan.StepStatus.PENDING) {
                                s.status = Plan.StepStatus.SKIPPED;
                            }
                        }
                        listener.onPlanDone(plan);
                        return;
                    }

                    Plan.Step step = plan.steps.get(i);
                    if (step.status == Plan.StepStatus.DONE
                            || step.status == Plan.StepStatus.SKIPPED) {
                        i++;
                        continue;
                    }

                    boolean success = runStep(ctx, config, plan, step, listener);

                    if (success) {
                        i++;
                        continue;
                    }

                    // ---------- Retry loop ----------
                    boolean retried = false;
                    for (int r = 0; r < MAX_STEP_RETRIES; r++) {
                        if (listener.isCancelled()) break;
                        step.attempts++;
                        step.status = Plan.StepStatus.RETRYING;
                        listener.onStepRetrying(step.index);
                        boolean retrySuccess = runStep(ctx, config, plan, step, listener);
                        if (retrySuccess) {
                            retried = true;
                            break;
                        }
                    }

                    if (retried) {
                        i++;
                        continue;
                    }

                    // ---------- Replan ----------
                    if (replanCount >= MAX_REPLANS || listener.isCancelled()) {
                        // No more replans. Mark failed, skip ahead.
                        step.status = Plan.StepStatus.FAILED;
                        step.finishedAt = System.currentTimeMillis();
                        listener.onStepFinished(step.index, false, step.result);
                        i++;
                        continue;
                    }

                    replanCount++;
                    String reason = "Step " + (step.index + 1) + " failed: " + step.result;
                    listener.onReplanning(step.index, reason);

                    boolean revised = replanSync(ctx, config, plan, step.index, listener);
                    if (!revised) {
                        // Replan failed; mark this step failed and stop.
                        step.status = Plan.StepStatus.FAILED;
                        step.finishedAt = System.currentTimeMillis();
                        listener.onStepFinished(step.index, false, step.result);
                        plan.status = Plan.OverallStatus.FAILED;
                        listener.onPlanDone(plan);
                        return;
                    }

                    // Plan was replaced. Restart iteration from the same logical position.
                    // Reset i so we re-enter the new step at position `step.index`.
                    i = step.index;
                }

                plan.status = Plan.OverallStatus.DONE;
                listener.onPlanDone(plan);
            }
        }).start();
    }

    // ------------------------------------------------------------------
    //  Step execution
    // ------------------------------------------------------------------

    private static boolean runStep(Context ctx, ApiConfig config, Plan plan,
                                   Plan.Step step, Listener listener) {
        step.status = Plan.StepStatus.RUNNING;
        step.startedAt = System.currentTimeMillis();
        listener.onStepStarted(step.index);

        JSONArray messages = new JSONArray();
        put(messages, "system", STEP_SYSTEM);
        put(messages, "user", buildStepContext(plan, step));

        for (int iter = 0; iter < MAX_ITER_PER_STEP; iter++) {
            if (listener.isCancelled()) {
                step.status = Plan.StepStatus.SKIPPED;
                step.finishedAt = System.currentTimeMillis();
                listener.onStepFinished(step.index, false, "Cancelled");
                return false;
            }

            String raw = callApi(config, messages);
            if (raw == null) {
                step.result = "Model call failed.";
                step.finishedAt = System.currentTimeMillis();
                return false;
            }

            ToolParser.Result parsed = ToolParser.parse(raw);
            if (parsed.cleanedText != null && !parsed.cleanedText.trim().isEmpty()) {
                listener.onStepNote(step.index, parsed.cleanedText.trim());
                // Detect explicit "not possible" reply from the model
                String lower = parsed.cleanedText.toLowerCase();
                if (lower.startsWith("done: not possible")) {
                    step.status = Plan.StepStatus.FAILED;
                    step.result = parsed.cleanedText.trim();
                    step.finishedAt = System.currentTimeMillis();
                    return false;
                }
            }

            if (parsed.calls.isEmpty()) {
                step.status = Plan.StepStatus.DONE;
                step.result = (parsed.cleanedText == null || parsed.cleanedText.trim().isEmpty())
                        ? "Done." : parsed.cleanedText.trim();
                step.finishedAt = System.currentTimeMillis();
                return true;
            }

            Tools.Call call = parsed.calls.get(0);
            String toolResult = runToolSync(ctx, call);
            listener.onStepNote(step.index, "→ " + toolResult);

            // If the tool clearly failed, do not pretend success.
            boolean toolFailed = looksLikeFailure(toolResult);
            put(messages, "assistant", raw);
            put(messages, "user",
                    "TOOL_RESULT: " + toolResult + "\n"
                    + (toolFailed
                        ? "That looks like a failure. If you can achieve the step a different way, "
                          + "emit another tool_call now. Otherwise reply exactly: DONE: not possible — <reason>.\n"
                        : "If this step is complete, reply with a one-sentence summary starting with DONE:. "
                          + "Otherwise emit the next tool_call.\n"));
        }

        // Reached iteration limit
        step.result = "Reached step iteration limit without success.";
        step.finishedAt = System.currentTimeMillis();
        return false;
    }

    // ------------------------------------------------------------------
    //  Replanning
    // ------------------------------------------------------------------

    private static boolean replanSync(final Context ctx, final ApiConfig config,
                                      final Plan plan, final int failedIndex,
                                      final Listener listener) {
        final Object lock = new Object();
        final Plan[] result = new Plan[1];
        final boolean[] done = new boolean[1];

        Planner.replanRemaining(config, plan, failedIndex, null, new Planner.Callback() {
            @Override public void onPlan(Plan newPlan) {
                synchronized (lock) {
                    result[0] = newPlan;
                    done[0] = true;
                    lock.notifyAll();
                }
            }
            @Override public void onError(String reason) {
                synchronized (lock) {
                    result[0] = null;
                    done[0] = true;
                    lock.notifyAll();
                }
            }
        });

        synchronized (lock) {
            long deadline = System.currentTimeMillis() + 60000;
            while (!done[0]) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                try { lock.wait(remaining); } catch (InterruptedException e) { break; }
            }
        }

        if (result[0] == null) return false;

        // Replace remaining steps (from failedIndex to end) with new ones.
        plan.replaceRemainingFrom(failedIndex, result[0].steps);
        plan.status = Plan.OverallStatus.REVISING;
        listener.onPlanRevised(plan);
        return true;
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static boolean looksLikeFailure(String result) {
        if (result == null) return true;
        String r = result.toLowerCase();
        return r.startsWith("could not")
                || r.startsWith("couldn't")
                || r.startsWith("no focused")
                || r.startsWith("no scrollable")
                || r.startsWith("app not installed")
                || r.contains("not found")
                || r.contains("not enabled")
                || r.startsWith("tool produced no result")
                || r.contains("screenshot save failed")
                || r.contains("screenshot unavailable")
                || r.startsWith("tool error");
    }

    private static String buildStepContext(Plan plan, Plan.Step step) {
        StringBuilder sb = new StringBuilder();
        sb.append("Overall goal: ").append(plan.goal).append("\n");
        sb.append("Step ").append(step.index + 1)
          .append(" of ").append(plan.size()).append(": ")
          .append(step.description).append("\n");
        if (step.toolHint != null && !step.toolHint.isEmpty()) {
            sb.append("Suggested tool: ").append(step.toolHint).append("\n");
        }
        if (step.attempts > 0) {
            sb.append("This is attempt #").append(step.attempts + 1)
              .append(". Previous attempts failed. Try a different approach.\n");
        }
        sb.append("\nPrevious step results:\n");
        boolean any = false;
        for (int i = 0; i < step.index; i++) {
            Plan.Step prev = plan.steps.get(i);
            if (prev.status == Plan.StepStatus.DONE) {
                sb.append("- Step ").append(i + 1).append(": ")
                  .append(prev.result).append("\n");
                any = true;
            } else if (prev.status == Plan.StepStatus.FAILED) {
                sb.append("- Step ").append(i + 1)
                  .append(": FAILED — ").append(prev.result).append("\n");
                any = true;
            }
        }
        if (!any) sb.append("(none yet)\n");
        sb.append("\nExecute this step now.");
        return sb.toString();
    }

    private static String runToolSync(final Context ctx, final Tools.Call call) {
        final Object lock = new Object();
        final String[] out = new String[1];
        final boolean[] done = new boolean[1];

        ToolExecutor.execute(ctx, call, new ToolExecutor.Listener() {
            @Override public void onNeedsConfirmation(String summary, Runnable confirmAction) {
                synchronized (lock) {
                    out[0] = "Autonomous mode skipped action requiring confirmation: " + summary;
                    done[0] = true;
                    lock.notifyAll();
                }
            }
            @Override public void onResult(String result) {
                synchronized (lock) {
                    out[0] = result;
                    done[0] = true;
                    lock.notifyAll();
                }
            }
        });

        synchronized (lock) {
            long deadline = System.currentTimeMillis() + TOOL_TIMEOUT_MS;
            while (!done[0]) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                try { lock.wait(remaining); } catch (InterruptedException e) { break; }
            }
        }
        return out[0] == null ? "Tool produced no result (timeout)" : out[0];
    }

    private static String callApi(ApiConfig config, JSONArray messages) {
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            body.put("model", config.model);
            body.put("temperature", 0.3);
            body.put("max_tokens", 600);
            body.put("messages", messages);

            URL url = new URL(config.endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(40000);
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
            if (status < 200 || status >= 300) return null;

            InputStream in = conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            return new JSONObject(sb.toString())
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void put(JSONArray arr, String role, String content) {
        try {
            JSONObject o = new JSONObject();
            o.put("role", role);
            o.put("content", content);
            arr.put(o);
        } catch (Exception ignored) {}
    }

    private Executor() {}
}
