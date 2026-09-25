package com.indai.assistant;

import java.util.ArrayList;
import java.util.List;

public final class Providers {

    public static class Provider {
        public final String name;
        public final String endpoint;
        public final String defaultModel;
        public final String keyHint;
        public final boolean vision;
        public final boolean requiresKey;
        public final String[] models;

        public Provider(String name, String endpoint, String defaultModel,
                        String keyHint, boolean vision, boolean requiresKey,
                        String... extraModels) {
            this.name = name;
            this.endpoint = endpoint;
            this.defaultModel = defaultModel;
            this.keyHint = keyHint;
            this.vision = vision;
            this.requiresKey = requiresKey;
            List<String> list = new ArrayList<>();
            list.add(defaultModel);
            if (extraModels != null) {
                for (String m : extraModels) {
                    if (m != null && !m.isEmpty() && !m.equals(defaultModel)) {
                        list.add(m);
                    }
                }
            }
            this.models = list.toArray(new String[0]);
        }
    }

    public static final List<Provider> ALL = new ArrayList<>();
    static {
        // ============ Primary free provider (verified working) ============
        ALL.add(new Provider("Agnes AI",
                "https://apihub.agnes-ai.com/v1/chat/completions",
                "agnes-3.0-flash", "sk-...", true, true,
                "agnes-3.0-pro", "agnes-2.5-flash", "agnes-2.5-pro"));

        // ============ Working free providers ============
        ALL.add(new Provider("Groq",
                "https://api.groq.com/openai/v1/chat/completions",
                "llama-3.1-8b-instant", "gsk_...", false, true,
                "llama-3.3-70b-versatile",
                "llama-3.1-70b-versatile",
                "mixtral-8x7b-32768",
                "gemma2-9b-it"));

        ALL.add(new Provider("Cerebras",
                "https://api.cerebras.ai/v1/chat/completions",
                "llama3.1-8b", "csk-...", false, true,
                "llama3.1-70b",
                "llama-3.3-70b"));

        ALL.add(new Provider("Google Gemini",
                "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                "gemini-2.0-flash", "AIza...", true, true,
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash",
                "gemini-1.5-pro"));

        ALL.add(new Provider("Mistral",
                "https://api.mistral.ai/v1/chat/completions",
                "mistral-small-latest", "...", false, true,
                "mistral-medium-latest",
                "mistral-large-latest",
                "open-mistral-nemo"));

        ALL.add(new Provider("OpenRouter",
                "https://openrouter.ai/api/v1/chat/completions",
                "meta-llama/llama-3.1-8b-instruct:free", "sk-or-...", true, true,
                "meta-llama/llama-3.3-70b-instruct:free",
                "google/gemini-2.0-flash-exp:free",
                "mistralai/mistral-nemo:free"));

        ALL.add(new Provider("Hugging Face",
                "https://router.huggingface.co/v1/chat/completions",
                "meta-llama/Llama-3.3-70B-Instruct", "hf_...", true, true,
                "meta-llama/Llama-3.1-8B-Instruct",
                "Qwen/Qwen2.5-72B-Instruct",
                "mistralai/Mistral-7B-Instruct-v0.3",
                "google/gemma-2-9b-it"));

        ALL.add(new Provider("OpenAI",
                "https://api.openai.com/v1/chat/completions",
                "gpt-4o-mini", "sk-...", true, true,
                "gpt-4o",
                "gpt-4-turbo",
                "gpt-3.5-turbo"));

        ALL.add(new Provider("DeepSeek",
                "https://api.deepseek.com/chat/completions",
                "deepseek-chat", "sk-...", false, true,
                "deepseek-reasoner"));

        ALL.add(new Provider("Cloudflare Workers AI",
                "https://api.cloudflare.com/client/v4/accounts/YOUR_ACCOUNT_ID/ai/v1/chat/completions",
                "@cf/meta/llama-3.1-8b-instruct", "CF token", false, true,
                "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
                "@cf/qwen/qwen1.5-14b-chat-awq"));
    }

    public static Provider findByName(String name) {
        if (name == null) return null;
        for (Provider p : ALL) if (p.name.equals(name)) return p;
        return null;
    }

    public static Provider findByEndpoint(String endpoint) {
        if (endpoint == null) return null;
        for (Provider p : ALL) if (p.endpoint.equals(endpoint)) return p;
        return null;
    }

    private Providers() {}
}
