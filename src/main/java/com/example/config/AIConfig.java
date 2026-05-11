package com.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AIConfig {

    @Value("${ai.dashscope.api-key:}")
    private String dashScopeApiKey;

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl("https://dashscope.aliyuncs.com/api/v1")
                .defaultHeader("Authorization", "Bearer " + dashScopeApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
