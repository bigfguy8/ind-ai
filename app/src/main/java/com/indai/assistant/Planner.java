package com.indai.assistant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Planner {

    public interface Callback {
        void onPlan(Plan plan);
        void onError(String reason);
    }

    private static final String SYSTEM_PROMPT =
        "You are the planning module of an AI agent running on an Android phone.\n" +
        "The user gives you a goal. You produce a short, concrete plan of 3 to 8 steps.\n" +
        "Each step is ONE actionable unit the executor carries out in isolation.\n" +
        "\n" +
        "HARD RULES:\n" +
        "- Every step must be doable in under 10 seconds.\n" +
        "- Every step must be self-contained. Never write 'the previous step' or 'as before'.\n" +
        "- Every step's description must be a clear imperative sentence under 90 characters.\n" +
        "- If a step involves the phone, set the tool field to the exact tool name.\n" +
        "- If a step involves reasoning, omit the tool field entirely.\n" +
        "- Never invent tools. Never emit shell commands.\n" +
        "\n" +
        "PLANNING HINTS:\n" +
        "- For a UI navigation goal: plan `read_screen` first to see what's there, then click.\n" +
        "- For opening an app: single step with open_app.\n" +
        "- For a multi-app goal: open_app step, then read_screen step, then action steps.\n" +
        "- For informational goals: reason step, then verify step, then summarize step.\n" +
        "\n" +
        "Respond ONLY with JSON, no prose, matching this schema:\n" +
        "{\n" +
        "  \"steps\": [\n" +
        "    {\"description\": \"Open the Settings app\", \"tool\": \"open_app\"},\n" +
        "    {\"description\": \"Read the current screen to find the Bluetooth entry\"},\n" +
        "    {\"description\": \"Tap Bluetooth\", \"tool\": \"click_text\"}\n" +
        "  ]\n" +
        "}\n" +
        "\n" +
        "Available tools: open_app, open_url, click_text, click_description, " +
        "type_text, scroll, press_back, press_home, read_screen, see_screen, " +
        "list_tappable, click_at, set_reminder, list_reminders, cancel_reminder.";

    private static final String REPLAN_PROMPT =
        "You are revising the remaining steps of an in-progress agent plan.\n" +
        "Some steps already ran. One step just failed. Your job: produce a NEW list of " +
        "steps to accomplish what remains of the original goal, avoiding the failure.\n" +
        "\n" +
        "HARD RULES:\n" +
        "- Do NOT repeat steps that already succeeded.\n" +
        "- Do NOT repeat the exact approach that just failed — use a different strategy.\n" +
        "- If the goal is truly unachievable, return an empty steps array.\n" +
        "- Each step: self-contained, under 90 chars, imperative.\n" +
        "\n" +
        "Respond ONLY with JSON matching the same schema as before:\n" +
        "{\"steps\": [{\"description\": \"...\", \"tool\": \"...\"}]}";

    public static void plan(final ApiConfig config, final String goal,
                            final String memoryBlock, final Callback cb) {
        String system = SYSTEM_PROMPT;
        if (memoryBlock != null && !memoryBlock.isEmpty()) {
            system = system + "\n\nContext about the user:\n" + memoryBlock;
        }
        callModel(config, system, goal, cb, goal);
    }

    /**
     * Ask the model to re-plan remaining steps given the current state of the plan.
     * The failed step is the one at `failedIndex`.
     */
    public static void replanRemaining(final ApiConfig config, final Plan plan,
                                       final int failedIndex, final String memoryBlock,
                                       final Callback cb) {
        String system = REPLAN_PROMPT;
        if (memoryBlock != null && !memoryBlock.isEmpty()) {
            system = system + "\n\nContext about the user:\n" + memoryBlock;
        }
        String userMsg = buildReplanContext(plan, failedIndex);
        callModel(config, system, userMsg, cb, plan.goal);
    }

    private static String buildReplanContext(Plan plan, int failedIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("ORIGINAL GOAL: ").append(plan.goal).append("\n\n");
        sb.append("STATUS OF PRIOR STEPS:\n");
        for (int i = 0; i < plan.steps.size(); i++) {
            Plan.Step s = plan.steps.get(i);
            if (s.status == Plan.StepStatus.DONE) {
                sb.append("  ✓ Step ").append(i + 1).append(": ")
                  .append(s.description).append("\n");
                if (!s.result.isEmpty()) {
                    sb.append("      Result: ").append(s.result).append("\n");
                }
            }
        }
        sb.append("\nFAILED STEP: Step ").append(failedIndex + 1).append("\n");
        sb.append("  Description: ").append(plan.steps.get(failedIndex).description)
          .append("\n");
        sb.append("  Attempts: ").append(plan.steps.get(failedIndex).attempts).append("\n");
        sb.append("  Failure result: ")
          .append(plan.steps.get(failedIndex).result).append("\n\n");
        sb.append("Now produce a NEW list of steps for what remains, avoiding the failure. ");
        sb.append("The failed step must be addressed differently.");
        return sb.toString();
    }

    private static void callModel(final ApiConfig config, final String system,
                                  final String userMsg, final Callback cb,
                                  final String goalForPlan) {
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection conn = null;
                try {
                    JSONArray messages = new JSONArray();
                    JSONObject s = new JSONObject();
                    s.put("role", "system");
                    s.put("content", system);
                    messages.put(s);
                    JSONObject u = new JSONObject();
                    u.put("role", "user");
                    u.put("content", userMsg);
                    messages.put(u);

                    JSONObject body = new JSONObject();
                    body.put("model", config.model);
                    body.put("temperature", 0.2);
                    body.put("max_tokens", 800);
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
                        InputStream err = conn.getErrorStream();
                        cb.onError("Planner HTTP " + status + ": " + readAll(err));
                        return;
                    }
                    String raw = readAll(conn.getInputStream());
                    String content = new JSONObject(raw)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim();

                    Plan plan = parsePlan(goalForPlan, content);
                    if (plan == null) {
                        cb.onError("Planner returned unparseable content.");
                        return;
                    }
                    cb.onPlan(plan);
                } catch (Exception e) {
                    cb.onError("Planner error: " + e.getClass().getSimpleName()
                            + " " + e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    private static Plan parsePlan(String goal, String content) {
        try {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start < 0 || end <= start) return null;
            JSONObject o = new JSONObject(content.substring(start, end + 1));
            JSONArray arr = o.optJSONArray("steps");
            if (arr == null) return null;
            List<Plan.Step> steps = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject so = arr.getJSONObject(i);
                String desc = so.optString("description", "").trim();
                if (desc.isEmpty()) continue;
                String tool = so.isNull("tool") ? null : so.optString("tool", null);
                steps.add(new Plan.Step(steps.size(), desc, tool));
            }
            return new Plan(goal, steps);
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private Planner() {}
}
