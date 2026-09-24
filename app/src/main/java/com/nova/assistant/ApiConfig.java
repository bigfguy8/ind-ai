package com.nova.assistant;

public class ApiConfig {

    public String endpoint;
    public String apiKey;
    public String model;
    public String systemPrompt;
    public String visionModel;

    // Fallback provider — used when primary returns 429 or 5xx
    public String fallbackEndpoint;
    public String fallbackApiKey;
    public String fallbackModel;

    // Vision provider — used for image requests (leave blank to use primary)
    public String visionEndpoint;
    public String visionApiKey;

    public ApiConfig(String endpoint, String apiKey, String model,
                     String systemPrompt, String visionModel) {
        this(endpoint, apiKey, model, systemPrompt, visionModel,
                "", "", "", "", "");
    }

    public ApiConfig(String endpoint, String apiKey, String model,
                     String systemPrompt) {
        this(endpoint, apiKey, model, systemPrompt, "",
                "", "", "", "", "");
    }

    public ApiConfig(String endpoint, String apiKey, String model,
                     String systemPrompt, String visionModel,
                     String fallbackEndpoint, String fallbackApiKey, String fallbackModel,
                     String visionEndpoint, String visionApiKey) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.visionModel = visionModel;
        this.fallbackEndpoint = fallbackEndpoint == null ? "" : fallbackEndpoint;
        this.fallbackApiKey = fallbackApiKey == null ? "" : fallbackApiKey;
        this.fallbackModel = fallbackModel == null ? "" : fallbackModel;
        this.visionEndpoint = visionEndpoint == null ? "" : visionEndpoint;
        this.visionApiKey = visionApiKey == null ? "" : visionApiKey;
    }

    public boolean hasFallback() {
        return fallbackEndpoint != null
                && !fallbackEndpoint.trim().isEmpty()
                && fallbackApiKey != null
                && !fallbackApiKey.trim().isEmpty();
    }

    public ApiConfig fallbackOnly() {
        return new ApiConfig(
                fallbackEndpoint,
                fallbackApiKey,
                fallbackModel == null || fallbackModel.trim().isEmpty()
                        ? model : fallbackModel,
                systemPrompt,
                visionModel,
                "", "", "",
                visionEndpoint, visionApiKey);
    }

    public boolean hasVisionProvider() {
        return visionEndpoint != null
                && !visionEndpoint.trim().isEmpty()
                && visionApiKey != null
                && !visionApiKey.trim().isEmpty();
    }

    public ApiConfig visionOnly() {
        return new ApiConfig(
                visionEndpoint,
                visionApiKey,
                visionModel == null || visionModel.trim().isEmpty()
                        ? model : visionModel,
                systemPrompt,
                visionModel,
                "", "", "", "", "");
    }
}
