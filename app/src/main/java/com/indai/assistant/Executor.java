package com.indai.assistant;

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

    private static final int MAX_ITER_PER_STEP = 2;
    private static final int TOOL_TIMEOUT_MS = 10000;
    private static final int MAX_STEP_RETRIES = 1;
    private static final int MAX_REPLANS = 1;
    private static final long API_RETRY_DELAY_MS = 3500;
    private static final long MIN_CALL_GAP_MS = 2000;
    private static long lastCallMs = 0;

    private static final String STEP_SYSTEM =
        "You are the execution module of an AI agent running on an Android phone. " +
        "You are given ONE step of a plan. Carry it out.\n" +
        "\n" +
        "Rules:\n" +
        "- If the step needs a device action, emit exactly one tool call in this form:\n" +
        "  <tool_call>{\"name\":\"open_app\",\"arguments\":{\"package\":\"com.android.settings\"}}</tool_call>\n" +
        "- Available tools: open_app, open_url, click_text, click_description, " +
        "type_text, scroll, press_back, press_home, read_screen, see_screen, " +
        "list_tappable, click_at, set_reminder, list_reminders, cancel_reminder, " +
        "send_sms, make_call, set_alarm, navigate_to, share_text, search_web, " +
        "play_music, get_time.\n" +
        "- If the step is informational, just answer it directly in one or two sentences.\n" +
        "- After a tool result comes back, reply with ONE sentence summarizing what happened.\n" +
        "- If a step cannot be done, reply: DONE: not possible — <short reason>.\n" +
        "- Never invent tools. Never output shell commands.\n" +
        "- CRITICAL: If a tool returns a result containing 'disconnected', 'not enabled', " +
        "'__BLOCKED_ACCESSIBILITY__', 'could not', 'no focused', or any error, you MUST NOT " +
        "claim success. Reply exactly: DONE: not possible — <short reason>.\n" +
        "- Do NOT retry a tool that just told you it cannot run.\n" +
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

                    FailureClassifier.Type failureType =
                            FailureClassifier.classify(step.result);
                    listener.onStepNote(step.index,
                            "[failure: " + failureType.name() + "]");

                    boolean retried = false;
                    if (FailureClassifier.isRetryable(failureType)) {
                        long delay = FailureClassifier.retryDelayMs(failureType);
                        for (int r = 0; r < MAX_STEP_RETRIES; r++) {
                            if (listener.isCancelled()) break;
                            step.attempts++;
                            step.status = Plan.StepStatus.RETRYING;
                            listener.onStepRetrying(step.index);
                            if (delay > 0) {
                                try { Thread.sleep(delay); }
                                catch (InterruptedException ignored) {}
                            }
                            boolean retrySuccess = runStep(ctx, config, plan, step, listener);
                            if (retrySuccess) { retried = true; break; }
                            FailureClassifier.Type after =
                                    FailureClassifier.classify(step.result);
                            if (!FailureClassifier.isRetryable(after)) break;
                        }
                    } else {
                        listener.onStepNote(step.index,
                                "[no retry — " + failureType.name() + "]");
                    }
                    if (retried) { i++; continue; }

                    if (!FailureClassifier.shouldReplan(failureType)) {
                        step.status = Plan.StepStatus.FAILED;
                        step.result = FailureClassifier.humanMessage(
                                failureType, step.result);
                        step.finishedAt = System.currentTimeMillis();
                        listener.onStepFinished(step.index, false, step.result);
                        i++;
                        continue;
                    }

                    if (replanCount >= MAX_REPLANS || listener.isCancelled()) {
                        step.status = Plan.StepStatus.FAILED;
                        step.finishedAt = System.currentTimeMillis();
                        listener.onStepFinished(step.index, false, step.result);
                        i++;
                        continue;
                    }
                    replanCount++;
                    String reason = "Step " + (step.index + 1) + " failed: " + step.result;
                    listener.onReplanning(step.index, reason);

                    boolean revised = replanSync(config, plan, step.index, listener);
                    if (!revised) {
                        step.status = Plan.StepStatus.FAILED;
                        step.finishedAt = System.currentTimeMillis();
                        listener.onStepFinished(step.index, false, step.result);
                        plan.status = Plan.OverallStatus.FAILED;
                        listener.onPlanDone(plan);
                        return;
                    }
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

        boolean anyToolFailed = false;

        for (int iter = 0; iter < MAX_ITER_PER_STEP; iter++) {
            if (listener.isCancelled()) {
                step.status = Plan.StepStatus.SKIPPED;
                step.finishedAt = System.currentTimeMillis();
                listener.onStepFinished(step.index, false, "Cancelled");
                return false;
            }

            String raw = callApiWithRetry(config, messages);
            if (raw == null || raw.startsWith("__API_ERR__")) {
                step.result = raw == null ? "Model call returned null"
                        : raw.substring("__API_ERR__".length());
                step.finishedAt = System.currentTimeMillis();
                return false;
            }

            ToolParser.Result parsed = ToolParser.parse(raw);
            if (!parsed.cleanedText.isEmpty()) {
                listener.onStepNote(step.index, parsed.cleanedText);
                String lower = parsed.cleanedText.toLowerCase();
                if (lower.startsWith("done: not possible")) {
                    step.status = Plan.StepStatus.FAILED;
                    step.result = parsed.cleanedText;
                    step.finishedAt = System.currentTimeMillis();
                    return false;
                }
            }

            if (parsed.calls.isEmpty()) {
                // Model produced no tool call.
                // HARD RULE: if any tool this step failed, we do NOT accept a "DONE" claim.
                if (anyToolFailed) {
                    step.status = Plan.StepStatus.FAILED;
                    step.result = "Tool failed. " + (parsed.cleanedText.isEmpty()
                            ? "" : "Model said: " + parsed.cleanedText);
                    step.finishedAt = System.currentTimeMillis();
                    return false;
                }
                // Also reject claims of success when the text contains hedging without action
                String lower = parsed.cleanedText.toLowerCase();
                boolean emptyOrVague = parsed.cleanedText.isEmpty()
                        || lower.contains("cannot")
                        || lower.contains("can't")
                        || lower.contains("unable")
                        || lower.contains("not possible")
                        || lower.contains("requires")
                        || lower.contains("permission");
                if (emptyOrVague) {
                    step.status = Plan.StepStatus.FAILED;
                    step.result = parsed.cleanedText.isEmpty()
                            ? "No action taken." : parsed.cleanedText;
                    step.finishedAt = System.currentTimeMillis();
                    return false;
                }
                step.status = Plan.StepStatus.DONE;
                step.result = parsed.cleanedText;
                step.finishedAt = System.currentTimeMillis();
                return true;
            }

            Tools.Call call = parsed.calls.get(0);
            String toolResult = runToolSync(ctx, config, call);
            listener.onStepNote(step.index, "→ " + toolResult);

            // Hard-fail: accessibility blocked — do not retry, do not replan endlessly
            if (toolResult != null && toolResult.startsWith("__BLOCKED_ACCESSIBILITY__")) {
                step.status = Plan.StepStatus.FAILED;
                step.result = "Screen control is off. Cannot proceed with this step.";
                step.finishedAt = System.currentTimeMillis();
                return false;
            }

            boolean toolFailed = looksLikeFailure(toolResult);
            anyToolFailed = toolFailed;

            // ---- SINGLE-SHOT EXECUTION ----
            // If the tool succeeded, mark the step done immediately.
            // This avoids a second LLM call to "interpret" the result, halving
            // the total LLM calls per step. Only failed tools trigger a retry
            // iteration with a follow-up LLM call.
            if (!toolFailed) {
                step.status = Plan.StepStatus.DONE;
                step.result = toolResult;
                step.finishedAt = System.currentTimeMillis();
                return true;
            }

            // Tool failed — feed result back once for a single recovery attempt
            put(messages, "assistant", raw);
            put(messages, "user",
                    "TOOL_RESULT: " + toolResult + "\n"
                    + "That looks like a failure. If you can achieve the step differently, "
                    + "emit another tool_call now. Otherwise reply exactly: "
                    + "DONE: not possible — <reason>.\n");
        }

        step.result = anyToolFailed
                ? "Reached iteration limit with tool failures."
                : "Reached step iteration limit.";
        step.finishedAt = System.currentTimeMillis();
        return !anyToolFailed;
    }

    // ------------------------------------------------------------------
    //  Tool execution (sync wrapper handling SEE_SCREEN async)
    // ------------------------------------------------------------------

    private static String runToolSync(final Context ctx, final ApiConfig config,
                                      final Tools.Call call) {
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
                // SEE_SCREEN_PENDING is a placeholder — keep waiting for the real path
                if ("__SEE_SCREEN_PENDING__".equals(result)) {
                    return;
                }
                synchronized (lock) {
                    out[0] = result;
                    done[0] = true;
                    lock.notifyAll();
                }
            }
        });

        synchronized (lock) {
            long deadline = System.currentTimeMillis() + TOOL_TIMEOUT_MS * 3;
            while (!done[0]) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                try { lock.wait(remaining); } catch (InterruptedException e) { break; }
            }
        }

        String result = out[0] == null ? "Tool produced no result (timeout)" : out[0];

        // Handle SEE_SCREEN result: describe the image synchronously
        if (result.startsWith("__SEE_SCREEN_PATH__")) {
            return resolveScreenCapture(ctx, config, result);
        }
        return result;
    }

    /** Take `__SEE_SCREEN_PATH__<path>\n\n<tappable>` and return a vision description. */
    private static String resolveScreenCapture(Context ctx, ApiConfig config, String marker) {
        String body = marker.substring("__SEE_SCREEN_PATH__".length());
        int split = body.indexOf("\n\n");
        String path = split >= 0 ? body.substring(0, split).trim() : body.trim();
        String tappable = split >= 0 ? body.substring(split + 2).trim() : "";

        java.io.File f = new java.io.File(path);
        if (!f.exists()) {
            return "Screenshot file missing: " + path + "\n\n" + tappable;
        }

        final Object lock = new Object();
        final String[] desc = new String[1];
        final boolean[] done = new boolean[1];

        VisionClient.describeFile(ctx, config, f, null, new VisionClient.Callback() {
            @Override public void onSuccess(String r) {
                synchronized (lock) { desc[0] = r; done[0] = true; lock.notifyAll(); }
            }
            @Override public void onError(String err) {
                synchronized (lock) {
                    desc[0] = "(vision unavailable: " + err + ")";
                    done[0] = true; lock.notifyAll();
                }
            }
        });

        synchronized (lock) {
            long dl = System.currentTimeMillis() + 30000;
            while (!done[0]) {
                long rem = dl - System.currentTimeMillis();
                if (rem <= 0) break;
                try { lock.wait(rem); } catch (InterruptedException e) { break; }
            }
        }

        String text = desc[0] == null ? "(no vision response)" : desc[0];
        return "SCREEN CONTENT:\n" + text + "\n\nTAPPABLE ELEMENTS:\n" + tappable;
    }

    // ------------------------------------------------------------------
    //  Replanning
    // ------------------------------------------------------------------

    private static boolean replanSync(final ApiConfig config, final Plan plan,
                                      final int failedIndex, final Listener listener) {
        final Object lock = new Object();
        final Plan[] result = new Plan[1];
        final boolean[] done = new boolean[1];

        Planner.replanRemaining(config, plan, failedIndex, null, new Planner.Callback() {
            @Override public void onPlan(Plan newPlan) {
                synchronized (lock) { result[0] = newPlan; done[0] = true; lock.notifyAll(); }
            }
            @Override public void onError(String reason) {
                synchronized (lock) { result[0] = null; done[0] = true; lock.notifyAll(); }
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
                || r.contains("screenshot file missing")
                || r.startsWith("tool error")
                || r.startsWith("no sms app")
                || r.startsWith("no dialer")
                || r.startsWith("no maps")
                || r.startsWith("no browser")
                || r.startsWith("no music")
                || r.startsWith("no share")
                || r.contains("disconnected")
                || r.contains("accessibility service is not enabled")
                || r.contains("needs accessibility")
                || r.contains("[unverified —");
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
            String r = prev.result == null ? "" : prev.result;
            if (r.length() > 300) r = r.substring(0, 300) + " …";
            if (prev.status == Plan.StepStatus.DONE) {
                sb.append("- Step ").append(i + 1).append(": ")
                  .append(r).append("\n");
                any = true;
            } else if (prev.status == Plan.StepStatus.FAILED) {
                sb.append("- Step ").append(i + 1)
                  .append(": FAILED — ").append(r).append("\n");
                any = true;
            }
        }
        if (!any) sb.append("(none yet)\n");
        sb.append("\nExecute this step now.");
        return sb.toString();
    }

    private static String callApiWithRetry(ApiConfig config, JSONArray messages) {
        // Enforce minimum gap between calls to respect free-tier rate limits
        long now = System.currentTimeMillis();
        long wait = MIN_CALL_GAP_MS - (now - lastCallMs);
        if (wait > 0) {
            try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
        }
        lastCallMs = System.currentTimeMillis();
        String raw = callApi(config, messages);
        if (raw != null && raw.startsWith("__API_ERR__")) {
            // Retry once for transient errors (429, 5xx, timeout)
            if (isRetryable(raw)) {
                try { Thread.sleep(API_RETRY_DELAY_MS); }
                catch (InterruptedException ignored) {}
                raw = callApi(config, messages);
            }
        }
        return raw;
    }

    private static boolean isRetryable(String err) {
        String e = err.toLowerCase();
        return e.contains("429") || e.contains("500") || e.contains("502")
                || e.contains("503") || e.contains("504")
                || e.contains("timeout") || e.contains("socket")
                || e.contains("connect");
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
            if (status < 200 || status >= 300) {
                return "__API_ERR__HTTP " + status;
            }

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
            return "__API_ERR__" + e.getClass().getSimpleName() + ": " + e.getMessage();
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
