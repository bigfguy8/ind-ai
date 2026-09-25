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

public final class ApiDiagnostic {

    public static String test(ApiConfig config) {
        HttpURLConnection conn = null;
        long t0 = System.currentTimeMillis();
        try {
            JSONArray messages = new JSONArray();
            JSONObject u = new JSONObject();
            u.put("role", "user");
            u.put("content", "Reply with the single word: OK");
            messages.put(u);

            JSONObject body = new JSONObject();
            body.put("model", config.model);
            body.put("max_tokens", 10);
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
            long dt = System.currentTimeMillis() - t0;

            InputStream in = status >= 200 && status < 300
                    ? conn.getInputStream()
                    : conn.getErrorStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            StringBuilder out2 = new StringBuilder();
            out2.append("Endpoint: ").append(config.endpoint).append("\n");
            out2.append("Model: ").append(config.model).append("\n");
            out2.append("Key set: ").append(
                    (config.apiKey != null && !config.apiKey.isEmpty()) ? "yes" : "NO").append("\n");
            out2.append("Key prefix: ").append(
                    config.apiKey == null ? "(null)"
                    : config.apiKey.isEmpty() ? "(empty)"
                    : config.apiKey.substring(0, Math.min(8, config.apiKey.length())) + "...").append("\n");
            out2.append("HTTP: ").append(status).append("  (").append(dt).append(" ms)\n");
            String resp = sb.toString();
            if (resp.length() > 400) resp = resp.substring(0, 400) + "...";
            out2.append("Response: ").append(resp);
            return out2.toString();
        } catch (Exception e) {
            long dt = System.currentTimeMillis() - t0;
            return "Endpoint: " + config.endpoint + "\n"
                    + "Model: " + config.model + "\n"
                    + "Exception after " + dt + "ms: "
                    + e.getClass().getSimpleName() + " — " + e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private ApiDiagnostic() {}
}
