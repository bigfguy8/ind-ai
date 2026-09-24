package com.nova.assistant;

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
        "type_text, scroll, press_back, press_home, read_screen, " +
        "set_reminder, list_reminders, cancel_reminder.";

    public static void plan(final ApiConfig config, final String goal,
                            final String memoryBlock, final Callback cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection conn = null;
                try {
                    String system = SYSTEM_PROMPT;
                    if (memoryBlock != null && !memoryBlock.isEmpty()) {
                        system = system + "\n\nContext about the user:\n" + memoryBlock;
                    }
                    JSONArray messages = new JSONArray();
                    JSONObject s = new JSONObject();
                    s.put("role", "system");
                    s.put("content", system);
                    messages.put(s);
                    JSONObject u = new JSONObject();
                    u.put("role", "user");
                    u.put("content", goal);
                    messages.put(u);

                    JSONObject body = new JSONObject();
                    body.put("model", config.model);
                    body.put("temperature", 0.2);
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

                    Plan plan = parsePlan(goal, content);
                    if (plan == null) {
                        cb.onError("Planner returned unparseable content.");
                        return;
                    }
                    if (plan.size() == 0) {
                        cb.onError("Planner produced no steps.");
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
