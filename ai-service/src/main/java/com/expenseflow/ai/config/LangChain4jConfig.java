package com.expenseflow.ai.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    @Value("${langchain4j.open-ai.chat-model.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.api-key:sk-default}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name:deepseek-chat}")
    private String modelName;

    @Bean
    public OpenAiChatModel chatModel() {
        return OpenAiChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .modelName(modelName)
            .timeout(Duration.ofSeconds(30))
            .maxRetries(2)
            .logRequests(true)
            .logResponses(true)
            .build();
    }
}
