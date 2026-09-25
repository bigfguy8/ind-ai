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

public class FactExtractor {

    public interface Listener {
        void onFacts(java.util.List<String> facts);
    }

    private static final String[] TRIGGERS = {
        "remember", "note that", "keep in mind", "don't forget",
        "my name", "i am", "i'm ", "i prefer", "i like", "i hate",
        "my birthday", "my favorite", "my favourite", "i live",
        "i work", "i use", "call me"
    };

    private static final String EXTRACT_PROMPT =
        "You extract durable user facts from a single message. " +
        "Respond ONLY with a JSON array of short strings. " +
        "Each string must be a standalone fact about the user worth remembering long-term. " +
        "Rules:\n" +
        "- Ignore one-off requests, questions, opinions about the world, and chit-chat.\n" +
        "- Rewrite in third person: 'My name is Arya' becomes 'User's name is Arya'.\n" +
        "- Skip anything already implicit (e.g. the user's language).\n" +
        "- If nothing is worth remembering, return [].\n" +
        "Examples:\n" +
        "Input: remember my name is Arya\\nOutput: [\"User's name is Arya\"]\n" +
        "Input: I prefer dark mode\\nOutput: [\"User prefers dark mode\"]\n" +
        "Input: what's the weather?\\nOutput: []\n" +
        "Input: I live in Mumbai and I use Python daily\\nOutput: [\"User lives in Mumbai\",\"User uses Python daily\"]";

    public static boolean looksFactual(String message) {
        if (message == null) return false;
        String m = message.toLowerCase();
        for (String t : TRIGGERS) {
            if (m.contains(t)) return true;
        }
        return false;
    }

    public static void extract(final ApiConfig config, final String message,
                               final Listener listener) {
        if (!looksFactual(message)) {
            listener.onFacts(java.util.Collections.emptyList());
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection conn = null;
                try {
                    JSONArray messages = new JSONArray();
                    JSONObject sys = new JSONObject();
                    sys.put("role", "system");
                    sys.put("content", EXTRACT_PROMPT);
                    messages.put(sys);
                    JSONObject usr = new JSONObject();
                    usr.put("role", "user");
                    usr.put("content", message);
                    messages.put(usr);

                    JSONObject body = new JSONObject();
                    body.put("model", config.model);
                    body.put("temperature", 0.1);
                    body.put("max_tokens", 200);
                    body.put("messages", messages);

                    URL url = new URL(config.endpoint);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
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
                        listener.onFacts(java.util.Collections.emptyList());
                        return;
                    }
                    InputStream in = conn.getInputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(in));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject res = new JSONObject(sb.toString());
                    String content = res.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim();

                    java.util.List<String> facts = new java.util.ArrayList<>();
                    try {
                        int start = content.indexOf('[');
                        int end = content.lastIndexOf(']');
                        if (start >= 0 && end > start) {
                            JSONArray arr = new JSONArray(content.substring(start, end + 1));
                            for (int i = 0; i < arr.length(); i++) {
                                String f = arr.optString(i, "").trim();
                                if (!f.isEmpty() && f.length() < 200) facts.add(f);
                            }
                        }
                    } catch (Exception ignored) {}

                    listener.onFacts(facts);
                } catch (Exception e) {
                    listener.onFacts(java.util.Collections.emptyList());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }
}
