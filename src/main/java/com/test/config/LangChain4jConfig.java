package com.test.config;

import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChain4jConfig {

    @Bean
    public ChatModel chatModel(RefreshableChatModel refreshableChatModel) {
        return refreshableChatModel;
    }
}
