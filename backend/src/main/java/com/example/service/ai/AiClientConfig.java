package com.example.service.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Picks which {@link AiClient} the app actually talks to. Defaults to
 * Anthropic; set the property {@code ai.provider} (env var
 * {@code AI_PROVIDER}) to "openai" to switch back.
 */
@Configuration
public class AiClientConfig {

    @Bean
    @Primary
    public AiClient activeAiClient(
            @Value("${ai.provider:anthropic}") String provider,
            @Qualifier("openaiClient") AiClient openAiClient,
            @Qualifier("anthropicClient") AiClient anthropicClient) {
        return "openai".equalsIgnoreCase(provider) ? openAiClient : anthropicClient;
    }
}
