package com.nova.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SecureKeyStore {

    private static final String PREFS = "nova_config";
    private static final int PROMPT_VERSION = 2;

    private static final String FALLBACK_PROMPT =
        "You are Ind AI, an advanced personal AI assistant running on Android. " +
        "Understand → Plan → Act → Verify → Report. Be concise, capable, and honest about limits.";

    private final Context context;
    private final SharedPreferences prefs;

    public SecureKeyStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(ApiConfig config) {
        prefs.edit()
                .putString("endpoint", config.endpoint)
                .putString("api_key", config.apiKey)
                .putString("model", config.model)
                .putString("system_prompt", config.systemPrompt)
                .putString("vision_model", config.visionModel)
                .putInt("prompt_version", PROMPT_VERSION)
                .apply();
    }

    public ApiConfig load() {
        String savedKey = prefs.getString("api_key", "");
        if (savedKey.isEmpty()) savedKey = BuildConfig.NOVA_API_KEY;

        String prompt = resolvePrompt();

        return new ApiConfig(
                prefs.getString("endpoint", BuildConfig.NOVA_ENDPOINT),
                savedKey,
                prefs.getString("model", BuildConfig.NOVA_MODEL),
                prompt,
                prefs.getString("vision_model", "")
        );
    }

    private String resolvePrompt() {
        String saved = prefs.getString("system_prompt", "");
        int savedVersion = prefs.getInt("prompt_version", 0);

        // Migrate old default prompts to the new master prompt.
        boolean isOldDefault = saved.isEmpty()
                || saved.startsWith("You are NOVA,")
                || saved.startsWith("You are NOVA ")
                || saved.startsWith("You are Ind AI, a highly capable");

        if (isOldDefault || savedVersion < PROMPT_VERSION) {
            String fresh = loadDefaultPrompt();
            prefs.edit()
                    .putString("system_prompt", fresh)
                    .putInt("prompt_version", PROMPT_VERSION)
                    .apply();
            return fresh;
        }
        return saved;
    }

    public String loadDefaultPrompt() {
        try {
            InputStream is = context.getResources()
                    .openRawResource(R.raw.ind_ai_prompt);
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            br.close();
            String out = sb.toString().trim();
            return out.isEmpty() ? FALLBACK_PROMPT : out;
        } catch (Exception e) {
            return FALLBACK_PROMPT;
        }
    }
}
