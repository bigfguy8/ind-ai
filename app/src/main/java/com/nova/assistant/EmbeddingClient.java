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

public class EmbeddingClient {

    public interface Callback {
        void onVector(float[] v);
        void onFailure(String reason);
    }

    private final String endpoint;
    private final String apiKey;
    private volatile boolean unavailable = false;

    public EmbeddingClient(ApiConfig config) {
        this.endpoint = deriveEmbeddingsEndpoint(config.endpoint);
        this.apiKey = config.apiKey;
    }

    public boolean isUnavailable() { return unavailable; }
    public String getEndpoint() { return endpoint; }

    public void embed(final String text, final Callback cb) {
        if (unavailable) { cb.onFailure("embeddings unavailable"); return; }
        if (text == null || text.trim().isEmpty()) {
            cb.onVector(fallback(""));
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection conn = null;
                try {
                    JSONObject body = new JSONObject();
                    body.put("input", text);

                    URL url = new URL(endpoint);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    if (apiKey != null && !apiKey.isEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
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
                    BufferedReader br = new BufferedReader(new InputStreamReader(in));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    if (status < 200 || status >= 300) {
                        unavailable = true;
                        cb.onFailure("HTTP " + status);
                        return;
                    }

                    JSONObject json = new JSONObject(sb.toString());
                    JSONArray dataArr = json.optJSONArray("data");
                    if (dataArr == null || dataArr.length() == 0) {
                        unavailable = true;
                        cb.onFailure("no embedding in response");
                        return;
                    }
                    JSONArray emb = dataArr.getJSONObject(0).optJSONArray("embedding");
                    if (emb == null) {
                        unavailable = true;
                        cb.onFailure("no embedding array");
                        return;
                    }
                    float[] vec = new float[emb.length()];
                    for (int i = 0; i < emb.length(); i++) vec[i] = (float) emb.getDouble(i);
                    cb.onVector(vec);

                } catch (Exception e) {
                    cb.onFailure(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------
    //  Fallback: deterministic bag-of-words hashing (no API needed)
    // ------------------------------------------------------------------

    public static float[] fallback(String text) {
        final int DIM = 256;
        float[] v = new float[DIM];
        if (text == null) return v;
        String[] words = text.toLowerCase().split("[^a-z0-9]+");
        for (String w : words) {
            if (w.length() < 2) continue;
            int h = w.hashCode() & 0x7fffffff;
            v[h % DIM] += 1f;
        }
        normalize(v);
        return v;
    }

    public static void normalize(float[] v) {
        double sum = 0;
        for (float x : v) sum += x * x;
        double len = Math.sqrt(sum);
        if (len < 1e-9) return;
        for (int i = 0; i < v.length; i++) v[i] /= len;
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na < 1e-9 || nb < 1e-9) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static String deriveEmbeddingsEndpoint(String chatEndpoint) {
        if (chatEndpoint == null || chatEndpoint.isEmpty()) return "";
        if (chatEndpoint.endsWith("/chat/completions")) {
            return chatEndpoint.substring(0,
                    chatEndpoint.length() - "/chat/completions".length()) + "/embeddings";
        }
        int idx = chatEndpoint.indexOf("/v1/");
        if (idx >= 0) {
            return chatEndpoint.substring(0, idx) + "/v1/embeddings";
        }
        return chatEndpoint + "/embeddings";
    }
}
