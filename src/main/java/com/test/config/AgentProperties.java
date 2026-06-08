package com.test.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Validated
@Configuration
@ConfigurationProperties(prefix = "openai")
public class AgentProperties {

    @NotBlank
    private String apiKey;

    @NotBlank
    private String baseUrl = "https://openrouter.ai/api/v1";

    @NotBlank
    private String modelName = "gpt-4o-mini";

    @NotNull
    private Double temperature = 0.7;

    @Positive
    private int maxTokens = 1024;

    @Positive
    private long apiKeyTtlSeconds = 600;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public long getApiKeyTtlSeconds() { return apiKeyTtlSeconds; }
    public void setApiKeyTtlSeconds(long apiKeyTtlSeconds) { this.apiKeyTtlSeconds = apiKeyTtlSeconds; }
}
