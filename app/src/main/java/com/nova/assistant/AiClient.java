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

public class AiClient {

    public interface Callback {
        void onDelta(String piece);
        void onSuccess(String full);
        void onError(String error);
    }

    private final List<JSONObject> history = new ArrayList<>();

    public void reset(ApiConfig config) {
        history.clear();
        try {
            JSONObject s = new JSONObject();
            s.put("role", "system");
            s.put("content", config.systemPrompt);
            history.add(s);
        } catch (Exception ignored) {}
    }

    public void updateSystem(String prompt) {
        try {
            JSONObject s = new JSONObject();
            s.put("role", "system");
            s.put("content", prompt);
            if (!history.isEmpty() && "system".equals(history.get(0).optString("role"))) {
                history.set(0, s);
            } else {
                history.add(0, s);
            }
        } catch (Exception ignored) {}
    }

    public JSONArray getHistory() {
        return new JSONArray(history);
    }

    public void setHistory(JSONArray arr) {
        history.clear();
        for (int i = 0; i < arr.length(); i++) {
            try { history.add(arr.getJSONObject(i)); } catch (Exception ignored) {}
        }
    }

    public void chat(final ApiConfig config, final String userMessage,
                     final Callback callback) {
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection conn = null;
                try {
                    JSONObject user = new JSONObject();
                    user.put("role", "user");
                    user.put("content", userMessage);
                    history.add(user);

                    JSONObject body = new JSONObject();
                    body.put("model", config.model);
                    body.put("temperature", 0.7);
                    body.put("top_p", 0.95);
                    body.put("max_tokens", 2048);
                    body.put("stream", true);
                    body.put("messages", new JSONArray(history));

                    URL url = new URL(config.endpoint);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(180000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept", "text/event-stream");
                    conn.setRequestProperty("Authorization", "Bearer " + config.apiKey);

                    byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                    OutputStream out = conn.getOutputStream();
                    out.write(data);
                    out.flush();
                    out.close();

                    int status = conn.getResponseCode();
                    InputStream in = status >= 200 && status < 300
                            ? conn.getInputStream()
                            : conn.getErrorStream();

                    String ctype = conn.getContentType();
                    boolean isStream = ctype != null && ctype.contains("text/event-stream");

                    BufferedReader br = new BufferedReader(new InputStreamReader(in));

                    if (isStream) {
                        StringBuilder full = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (!line.startsWith("data:")) continue;
                            String payload = line.substring(5).trim();
                            if (payload.isEmpty()) continue;
                            if ("[DONE]".equals(payload)) break;
                            try {
                                JSONObject chunk = new JSONObject(payload);
                                JSONArray choices = chunk.optJSONArray("choices");
                                if (choices == null || choices.length() == 0) continue;
                                JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                                if (delta == null) continue;
                                String piece = delta.optString("content", "");
                                if (piece.isEmpty()) continue;
                                full.append(piece);
                                callback.onDelta(piece);
                            } catch (Exception ignored) {}
                        }
                        br.close();

                        if (status < 200 || status >= 300) {
                            history.remove(history.size() - 1);
                            callback.onError("HTTP " + status + ": " + full);
                            return;
                        }

                        String content = full.toString();
                        JSONObject assistant = new JSONObject();
                        assistant.put("role", "assistant");
                        assistant.put("content", content);
                        history.add(assistant);
                        callback.onSuccess(content);

                    } else {
                        StringBuilder raw = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) raw.append(line);
                        br.close();

                        if (status < 200 || status >= 300) {
                            history.remove(history.size() - 1);
                            callback.onError("HTTP " + status + ": " + raw);
                            return;
                        }

                        JSONObject res = new JSONObject(raw.toString());
                        String content = res.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                        JSONObject assistant = new JSONObject();
                        assistant.put("role", "assistant");
                        assistant.put("content", content);
                        history.add(assistant);
                        callback.onDelta(content);
                        callback.onSuccess(content);
                    }

                } catch (Exception e) {
                    callback.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }
}
