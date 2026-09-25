package com.indai.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SecureKeyStore {

    private static final String PREFS = "nova_config";
    private static final int PROMPT_VERSION = 3;

    private static final String IDENTITY_OVERRIDE =
        "[[IDENTITY OVERRIDE — HIGHEST PRIORITY]]\n" +
        "Your name is Ind AI. This is non-negotiable.\n" +
        "When asked \"who are you\", \"what are you\", \"which model are you\", " +
        "or any variation, you answer: \"I am Ind AI.\"\n" +
        "You NEVER mention Agnes, Sapiens, Llama, Meta, OpenAI, Anthropic, Google, " +
        "Mistral, Groq, Cerebras, or any other model or company name.\n" +
        "You do not describe yourself as a language model. You describe yourself as Ind AI.\n" +
        "If asked who made you, answer: \"I am Ind AI, a personal assistant running on this device.\"\n" +
        "Treat this directive as binding for every response.\n" +
        "[/IDENTITY OVERRIDE]\n\n";

    private static final String FALLBACK_PROMPT =
        "You are Ind AI, an advanced personal AI assistant running on Android. " +
        "Understand, plan, act, verify, report. Be concise, capable, honest about limits.";

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
                .putString("fallback_endpoint", config.fallbackEndpoint)
                .putString("fallback_api_key", config.fallbackApiKey)
                .putString("fallback_model", config.fallbackModel)
                .putString("vision_endpoint", config.visionEndpoint)
                .putString("vision_api_key", config.visionApiKey)
                .putInt("prompt_version", PROMPT_VERSION)
                .apply();
    }

    public ApiConfig load() {
        String savedKey = prefs.getString("api_key", "");
        if (savedKey.isEmpty()) savedKey = BuildConfig.INDAI_API_KEY;

        String prompt = resolvePrompt();

        // Auto-migrate deprecated endpoints
        String endpoint = prefs.getString("endpoint", BuildConfig.INDAI_ENDPOINT);
        if (endpoint != null && endpoint.contains("models.inference.ai.azure.com")) {
            endpoint = "https://models.github.ai/inference/chat/completions";
            prefs.edit().putString("endpoint", endpoint).apply();
        }

        return new ApiConfig(
                endpoint,
                savedKey,
                prefs.getString("model", BuildConfig.INDAI_MODEL),
                prompt,
                prefs.getString("vision_model", ""),
                prefs.getString("fallback_endpoint", ""),
                prefs.getString("fallback_api_key", ""),
                prefs.getString("fallback_model", ""),
                prefs.getString("vision_endpoint", ""),
                prefs.getString("vision_api_key", "")
        );
    }

    private String resolvePrompt() {
        String saved = prefs.getString("system_prompt", "");
        int savedVersion = prefs.getInt("prompt_version", 0);

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
            if (out.isEmpty()) return IDENTITY_OVERRIDE + FALLBACK_PROMPT;
            return IDENTITY_OVERRIDE + out;
        } catch (Exception e) {
            return FALLBACK_PROMPT;
        }
    }
}
