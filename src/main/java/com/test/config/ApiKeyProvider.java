package com.test.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;


@Component
public class ApiKeyProvider {
    private static final Logger log = LoggerFactory.getLogger(ApiKeyProvider.class);

    private final AgentProperties properties;

    private volatile String cachedKey;
    private volatile Instant cachedAt = Instant.EPOCH;

    public ApiKeyProvider(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * Returns the current API key.
     * Refreshes automatically if the TTL has expired.
     */
    public synchronized String getKey() {
        if (isFresh()) {
            return cachedKey;
        }
        return refresh();
    }

    /**
     * Forces a key refresh regardless of TTL.
     * Called by RefreshableChatModel on every 401.
     */
    public synchronized String refresh() {
        log.warn("Refreshing OpenAI API key...");

        String freshKey = fetchKeyFromSource();

        if (freshKey == null || freshKey.isBlank()) {
            throw new IllegalStateException(
                    "API key refresh failed: no valid key found from source"
            );
        }

        this.cachedKey = freshKey;
        this.cachedAt  = Instant.now();
        log.info("API key refreshed successfully at {}", cachedAt);
        return freshKey;
    }

    private boolean isFresh() {
        return cachedKey != null &&
                Instant.now().isBefore(
                        cachedAt.plusSeconds(properties.getApiKeyTtlSeconds())
                );
    }

    /**
     * Replace this method body with your real secret source.
     *
     * Option A: Environment variable (default)
     * Option B: AWS Secrets Manager
     * Option C: HashiCorp Vault
     * Option D: Azure Key Vault
     */
    private String fetchKeyFromSource() {
        // Option A: re-read from environment at refresh time
        String envKey = System.getenv("OPENAI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            log.debug("API key loaded from environment variable");
            return envKey;
        }

        // Fallback: use value from application.properties
        log.debug("API key loaded from application.properties");
        return properties.getApiKey();

        // Option B: AWS Secrets Manager
        // try (SecretsManagerClient client = SecretsManagerClient.create()) {
        //     GetSecretValueResponse resp = client.getSecretValue(
        //         GetSecretValueRequest.builder()
        //             .secretId("prod/openai/api-key")
        //             .build());
        //     return resp.secretString();
        // }

        // Option C: HashiCorp Vault (Spring Vault)
        // VaultResponse resp = vaultTemplate.read("secret/openai");
        // return (String) resp.getData().get("api-key");
    }
}
