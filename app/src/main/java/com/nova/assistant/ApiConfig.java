package com.nova.assistant;

public class ApiConfig {

    public String endpoint;
    public String apiKey;
    public String model;
    public String systemPrompt;
    public String visionModel;

    public ApiConfig(String endpoint, String apiKey, String model,
                     String systemPrompt, String visionModel) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.visionModel = visionModel;
    }

    public ApiConfig(String endpoint, String apiKey, String model,
                     String systemPrompt) {
        this(endpoint, apiKey, model, systemPrompt, "");
    }
}
