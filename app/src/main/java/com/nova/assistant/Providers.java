package com.nova.assistant;

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
        public Provider(String name, String endpoint, String defaultModel,
                        String keyHint, boolean vision, boolean requiresKey) {
            this.name = name;
            this.endpoint = endpoint;
            this.defaultModel = defaultModel;
            this.keyHint = keyHint;
            this.vision = vision;
            this.requiresKey = requiresKey;
        }
    }

    public static final List<Provider> ALL = new ArrayList<>();
    static {
        // ==== No-key providers (truly free) ====
        ALL.add(new Provider("Pollinations (no key)",
                "https://text.pollinations.ai/v1/chat/completions",
                "openai-fast", "no key needed", true, false));
        ALL.add(new Provider("KeylessAI (no key)",
                "https://keylessai.thryx.workers.dev/v1/chat/completions",
                "llama-3.1-8b-instant", "no key needed", false, false));

        // ==== High-limit free providers ====
        ALL.add(new Provider("Groq",
                "https://api.groq.com/openai/v1/chat/completions",
                "llama-3.1-8b-instant", "gsk_...", false, true));
        ALL.add(new Provider("Cerebras",
                "https://api.cerebras.ai/v1/chat/completions",
                "llama3.1-8b", "csk-...", false, true));
        ALL.add(new Provider("Google Gemini",
                "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                "gemini-2.0-flash", "AIza...", true, true));
        ALL.add(new Provider("Mistral",
                "https://api.mistral.ai/v1/chat/completions",
                "mistral-small-latest", "...", false, true));
        ALL.add(new Provider("OpenRouter",
                "https://openrouter.ai/api/v1/chat/completions",
                "meta-llama/llama-3.1-8b-instruct:free", "sk-or-...", true, true));
        ALL.add(new Provider("GitHub Models",
                "https://models.inference.ai.azure.com/chat/completions",
                "gpt-4o-mini", "ghp_... (PAT)", true, true));
        ALL.add(new Provider("Cloudflare Workers AI",
                "https://api.cloudflare.com/client/v4/accounts/YOUR_ACCOUNT_ID/ai/v1/chat/completions",
                "@cf/meta/llama-3.1-8b-instruct", "CF token", false, true));

        // ==== Paid providers ====
        ALL.add(new Provider("Agnes AI",
                "https://apihub.agnes-ai.com/v1/chat/completions",
                "agnes-3.0-flash", "sk-...", true, true));
        ALL.add(new Provider("OpenAI",
                "https://api.openai.com/v1/chat/completions",
                "gpt-4o-mini", "sk-...", true, true));
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
