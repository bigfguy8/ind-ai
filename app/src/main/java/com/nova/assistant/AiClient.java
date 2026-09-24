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

    public JSONArray getHistory() { return new JSONArray(history); }

    public void setHistory(JSONArray arr) {
        history.clear();
        for (int i = 0; i < arr.length(); i++) {
            try { history.add(arr.getJSONObject(i)); } catch (Exception ignored) {}
        }
    }

    public void chat(final ApiConfig config, final String userMessage, final Callback callback) {
        new Thread(new Runnable() {
            @Override public void run() {
                JSONObject user = new JSONObject();
                try {
                    user.put("role", "user");
                    user.put("content", userMessage);
                } catch (Exception ignored) {}
                history.add(user);

                boolean[] emitted = {false};
                String err = tryRequest(config, callback, emitted);

                if (err == null) return;

                // Try fallback only if:
                // - Primary failed BEFORE emitting any tokens (so user hasn't seen partial output)
                // - Fallback is configured
                // - Error is a retryable status (429 / 5xx / network)
                if (!emitted[0] && config.hasFallback() && isRetryable(err)) {
                    callback.onDelta("\n[primary unavailable — switching to fallback...]\n");
                    ApiConfig fb = config.fallbackOnly();
                    boolean[] emitted2 = {false};
                    String err2 = tryRequest(fb, callback, emitted2);
                    if (err2 == null) return;
                    history.remove(history.size() - 1);
                    callback.onError("Primary: " + err + "\nFallback: " + err2);
                    return;
                }

                history.remove(history.size() - 1);
                callback.onError(err);
            }
        }).start();
    }

    // Returns null on success, or an error string on failure.
    private String tryRequest(ApiConfig config, Callback callback, boolean[] emitted) {
        HttpURLConnection conn = null;
        try {
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
            if (config.apiKey != null && !config.apiKey.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + config.apiKey);
            }

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
                        emitted[0] = true;
                        callback.onDelta(piece);
                    } catch (Exception ignored) {}
                }
                br.close();

                if (status < 200 || status >= 300) {
                    return "HTTP " + status + ": " + full;
                }

                String content = sanitizeIdentity(full.toString());
                JSONObject assistant = new JSONObject();
                assistant.put("role", "assistant");
                assistant.put("content", content);
                history.add(assistant);
                callback.onSuccess(content);
                return null;

            } else {
                StringBuilder raw = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) raw.append(line);
                br.close();

                if (status < 200 || status >= 300) {
                    return "HTTP " + status + ": " + raw;
                }

                JSONObject res = new JSONObject(raw.toString());
                String content = sanitizeIdentity(res.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content"));
                JSONObject assistant = new JSONObject();
                assistant.put("role", "assistant");
                assistant.put("content", content);
                history.add(assistant);
                emitted[0] = true;
                callback.onDelta(content);
                callback.onSuccess(content);
                return null;
            }

        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Replace any identity claims about the underlying model with Ind AI. */
    public static String sanitizeIdentity(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = text;
        // Provider / model identity — Agnes
        out = out.replaceAll("(?i)Agnes[- ]?3(?:\\.0)?(?:[- ]?flash)?", "Ind AI");
        out = out.replaceAll("(?i)Agnes[- ]?[0-9]+(?:\\.[0-9]+)?(?:[- ]?[a-z]+)?", "Ind AI");
        out = out.replaceAll("(?i)Sapiens AI", "the Ind AI project");
        out = out.replaceAll("(?i)Sapiens", "Ind AI");
        // Generic AI self-descriptions
        out = out.replaceAll("(?i)I am (?:an? )?(?:AI )?assistant developed by [^.,!?]+", "I am Ind AI");
        out = out.replaceAll("(?i)I'm (?:an? )?(?:AI )?assistant developed by [^.,!?]+", "I am Ind AI");
        out = out.replaceAll("(?i)I am (?:an? )?(?:AI )?language model(?: developed by [^.,!?]+)?", "I am Ind AI");
        out = out.replaceAll("(?i)I'm (?:an? )?(?:AI )?language model(?: developed by [^.,!?]+)?", "I am Ind AI");
        out = out.replaceAll("(?i)I am (?:an? )?(?:LLM|large language model)(?: developed by [^.,!?]+)?", "I am Ind AI");
        return out;
    }

    private static boolean isRetryable(String err) {
        if (err == null) return false;
        // Any HTTP 4xx other than 429 is a permanent failure — do not retry.
        // HTTP 5xx and network errors are retryable.
        if (err.startsWith("HTTP ")) {
            try {
                int code = Integer.parseInt(err.substring(5, 8).trim());
                return code == 429 || code >= 500;
            } catch (Exception e) {
                return false;
            }
        }
        // Network-layer exceptions — retry
        return err.contains("SocketException")
                || err.contains("UnknownHostException")
                || err.contains("ConnectException")
                || err.contains("SocketTimeoutException");
    }
}
