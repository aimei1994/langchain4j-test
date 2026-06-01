package com.test.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
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
}
