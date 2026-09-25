package com.indai.assistant;

public class ApiConfig {

    public String endpoint;
    public String apiKey;
    public String model;
    public String systemPrompt;
    public String visionModel;

    public String fallbackEndpoint;
    public String fallbackApiKey;
    public String fallbackModel;

    public String visionEndpoint;
    public String visionApiKey;

    public String primaryProviderName;
    public String fallbackProviderName;
    public String visionProviderName;

    public ApiConfig(String endpoint, String apiKey, String model,
                     String systemPrompt, String visionModel) {
        this(endpoint, apiKey, model, systemPrompt, visionModel,
                "", "", "", "", "", "", "", "");
    }

    public ApiConfig(String endpoint, String apiKey, String model,
                     String systemPrompt) {
        this(endpoint, apiKey, model, systemPrompt, "",
                "", "", "", "", "", "", "", "");
    }

    public ApiConfig(String endpoint, String apiKey, String model,
                     String systemPrompt, String visionModel,
                     String fallbackEndpoint, String fallbackApiKey, String fallbackModel,
                     String visionEndpoint, String visionApiKey) {
        this(endpoint, apiKey, model, systemPrompt, visionModel,
                fallbackEndpoint, fallbackApiKey, fallbackModel,
                visionEndpoint, visionApiKey, "", "", "");
    }

    public ApiConfig(String endpoint, String apiKey, String model,
                     String systemPrompt, String visionModel,
                     String fallbackEndpoint, String fallbackApiKey, String fallbackModel,
                     String visionEndpoint, String visionApiKey,
                     String primaryProviderName, String fallbackProviderName,
                     String visionProviderName) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.visionModel = visionModel;
        this.fallbackEndpoint = nn(fallbackEndpoint);
        this.fallbackApiKey = nn(fallbackApiKey);
        this.fallbackModel = nn(fallbackModel);
        this.visionEndpoint = nn(visionEndpoint);
        this.visionApiKey = nn(visionApiKey);
        this.primaryProviderName = nn(primaryProviderName);
        this.fallbackProviderName = nn(fallbackProviderName);
        this.visionProviderName = nn(visionProviderName);
    }

    private static String nn(String s) { return s == null ? "" : s; }

    public boolean hasFallback() {
        return !fallbackEndpoint.trim().isEmpty()
                && !fallbackApiKey.trim().isEmpty();
    }

    public ApiConfig fallbackOnly() {
        return new ApiConfig(
                fallbackEndpoint,
                fallbackApiKey,
                fallbackModel.trim().isEmpty() ? model : fallbackModel,
                systemPrompt,
                visionModel,
                "", "", "", visionEndpoint, visionApiKey,
                fallbackProviderName, "", "");
    }

    public boolean hasVisionProvider() {
        return !visionEndpoint.trim().isEmpty()
                && !visionApiKey.trim().isEmpty();
    }

    public ApiConfig visionOnly() {
        return new ApiConfig(
                visionEndpoint,
                visionApiKey,
                visionModel.trim().isEmpty() ? model : visionModel,
                systemPrompt,
                visionModel,
                "", "", "", "", "",
                visionProviderName, "", "");
    }
}
