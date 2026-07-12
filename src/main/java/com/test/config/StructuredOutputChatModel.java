package com.test.config;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

// Same key-refresh behavior as RefreshableChatModel, but built with the
// json_schema response format enabled so AiServices structured-output
// return types (POJO / List<Pojo> / Result<...>) work against it.
// Kept separate from the plain `chatModel` bean so free-text flows
// (SkillService, non-structured subagents) are unaffected.
@Component
public class StructuredOutputChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputChatModel.class);

    private final ApiKeyProvider keyProvider;
    private final AgentProperties properties;

    private volatile ChatModel delegate;

    public StructuredOutputChatModel(ApiKeyProvider keyProvider, AgentProperties properties) {
        this.keyProvider = keyProvider;
        this.properties  = properties;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            return getDelegate().chat(request);

        } catch (Exception e) {
            if (is401(e)) {
                log.warn("401 Unauthorized detected — refreshing key and retrying once");
                rebuildDelegate();
                return delegate.chat(request);
            }
            throw e;
        }
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return getDelegate().supportedCapabilities();
    }

    private synchronized void rebuildDelegate() {
        String freshKey = keyProvider.refresh();
        delegate = buildModel(freshKey);
        log.info("Structured-output ChatModel rebuilt with refreshed API key");
    }

    private ChatModel getDelegate() {
        if (delegate == null) {
            synchronized (this) {
                if (delegate == null) {
                    delegate = buildModel(keyProvider.getKey());
                }
            }
        }
        return delegate;
    }

    private ChatModel buildModel(String apiKey) {
        return OpenAiChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(apiKey)
                .modelName(properties.getModelName())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .responseFormat("json_schema")
                .strictJsonSchema(true)
                .logResponses(true)
                .logRequests(true)
                .build();
    }

    private boolean is401(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("401")
                || msg.contains("Unauthorized")
                || msg.contains("invalid_api_key");
    }
}
