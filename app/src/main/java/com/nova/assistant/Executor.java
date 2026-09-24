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
        void onPlanDone(Plan plan);
        boolean isCancelled();
    }

    private static final int MAX_ITER_PER_STEP = 3;
    private static final int TOOL_TIMEOUT_MS = 8000;

    private static final String STEP_SYSTEM =
        "You are the execution module of an AI agent running on an Android phone. " +
        "You are given ONE step of a plan. Carry it out.\n" +
        "\n" +
        "Rules:\n" +
        "- If the step needs a device action, emit exactly one tool call in this form:\n" +
        "  <tool_call>{\"name\":\"open_app\",\"arguments\":{\"package\":\"com.android.settings\"}}</tool_call>\n" +
        "- Available tools: open_app, open_url, click_text, click_description, " +
        "type_text, scroll, press_back, press_home, read_screen, " +
        "set_reminder, list_reminders, cancel_reminder.\n" +
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
                for (Plan.Step step : plan.steps) {
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
                    runStep(ctx, config, plan, step, listener);
                }
                plan.status = Plan.OverallStatus.DONE;
                listener.onPlanDone(plan);
            }
        }).start();
    }

    private static void runStep(Context ctx, ApiConfig config, Plan plan,
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
                return;
            }

            String raw = callApi(config, messages);
            if (raw == null) {
                step.status = Plan.StepStatus.FAILED;
                step.result = "Model call failed.";
                step.finishedAt = System.currentTimeMillis();
                listener.onStepFinished(step.index, false, step.result);
                return;
            }

            ToolParser.Result parsed = ToolParser.parse(raw);
            if (parsed.cleanedText != null && !parsed.cleanedText.trim().isEmpty()) {
                listener.onStepNote(step.index, parsed.cleanedText.trim());
            }

            if (parsed.calls.isEmpty()) {
                step.status = Plan.StepStatus.DONE;
                step.result = (parsed.cleanedText == null || parsed.cleanedText.trim().isEmpty())
                        ? "Done." : parsed.cleanedText.trim();
                step.finishedAt = System.currentTimeMillis();
                listener.onStepFinished(step.index, true, step.result);
                return;
            }

            Tools.Call call = parsed.calls.get(0);
            String toolResult = runToolSync(ctx, call);
            listener.onStepNote(step.index, "→ " + toolResult);

            put(messages, "assistant", raw);
            put(messages, "user",
                    "TOOL_RESULT: " + toolResult + "\n"
                    + "If this step is complete, reply with a one-sentence summary starting with DONE:. "
                    + "Otherwise emit the next tool_call.");
        }

        step.status = Plan.StepStatus.DONE;
        step.result = "Reached step iteration limit.";
        step.finishedAt = System.currentTimeMillis();
        listener.onStepFinished(step.index, true, step.result);
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
            conn.setRequestProperty("Authorization", "Bearer " + config.apiKey);

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
