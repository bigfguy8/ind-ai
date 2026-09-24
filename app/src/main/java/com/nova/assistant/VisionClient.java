package com.nova.assistant;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class VisionClient {

    public interface Callback {
        void onSuccess(String response);
        void onError(String error);
    }

    private static final int MAX_DIM = 1024;
    private static final int JPEG_QUALITY = 72;

    public static void describe(final Context ctx, final ApiConfig config,
                                final Uri imageUri, final String userPrompt,
                                final Callback cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection conn = null;
                try {
                    String base64 = loadAndEncode(ctx, imageUri);
                    if (base64 == null) {
                        cb.onError("Could not read image.");
                        return;
                    }

                    String model = visionModel(config);
                    JSONArray content = new JSONArray();
                    JSONObject textPart = new JSONObject();
                    textPart.put("type", "text");
                    textPart.put("text", userPrompt == null || userPrompt.isEmpty()
                            ? "Describe this image in detail." : userPrompt);
                    content.put(textPart);

                    JSONObject imgPart = new JSONObject();
                    imgPart.put("type", "image_url");
                    JSONObject imgUrl = new JSONObject();
                    imgUrl.put("url", "data:image/jpeg;base64," + base64);
                    imgPart.put("image_url", imgUrl);
                    content.put(imgPart);

                    JSONObject userMsg = new JSONObject();
                    userMsg.put("role", "user");
                    userMsg.put("content", content);

                    JSONArray messages = new JSONArray();
                    JSONObject sys = new JSONObject();
                    sys.put("role", "system");
                    sys.put("content", config.systemPrompt);
                    messages.put(sys);
                    messages.put(userMsg);

                    JSONObject body = new JSONObject();
                    body.put("model", model);
                    body.put("max_tokens", 1200);
                    body.put("messages", messages);

                    URL url = new URL(config.endpoint);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(120000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
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

                    BufferedReader br = new BufferedReader(new InputStreamReader(in));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    if (status < 200 || status >= 300) {
                        cb.onError("Vision HTTP " + status + ": " + sb);
                        return;
                    }

                    JSONObject res = new JSONObject(sb.toString());
                    String reply = res.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                    cb.onSuccess(reply);

                } catch (Exception e) {
                    cb.onError("Vision error: " + e.getClass().getSimpleName()
                            + " " + e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    private static String visionModel(ApiConfig config) {
        String m = config.visionModel;
        if (m == null || m.trim().isEmpty()) return config.model;
        return m;
    }

    private static String loadAndEncode(Context ctx, Uri uri) {
        try {
            InputStream is = ctx.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            Bitmap original = BitmapFactory.decodeStream(is);
            is.close();
            if (original == null) return null;

            Bitmap scaled = scaleDown(original, MAX_DIM);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
            byte[] bytes = baos.toByteArray();
            if (scaled != original) scaled.recycle();
            original.recycle();
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap scaleDown(Bitmap src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longer = Math.max(w, h);
        if (longer <= maxDim) return src;
        float ratio = (float) maxDim / longer;
        int nw = Math.round(w * ratio);
        int nh = Math.round(h * ratio);
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    private VisionClient() {}
}
