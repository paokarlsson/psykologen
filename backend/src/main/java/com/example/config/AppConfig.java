package com.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.service.BackgroundSessionUpdater;
import com.example.service.PsykologenService;
import com.example.service.ai.AiClient;
import com.example.service.ai.AnthropicChatClient;
import com.example.service.ai.OpenAiClient;
import com.example.storage.FileSessionArtifactStore;
import com.example.storage.SessionArtifactStore;

/**
 * Composition root: den enda plats i appen där konkreta implementationer
 * kopplas ihop. Klasserna som faktiskt gör jobbet ({@link AnthropicChatClient},
 * {@link OpenAiClient}, {@link PsykologenService}, {@link BackgroundSessionUpdater},
 * {@link FileSessionArtifactStore}, ...) är medvetet ramverksfria - inga
 * {@code @Component}/{@code @Service}/{@code @Autowired}/{@code @Value} på
 * dem själva. Allt de behöver kommer in via konstruktorn, och den
 * ledningsdragningen sker uteslutande här.
 *
 * {@link com.example.controller.PsykologenController} måste fortfarande
 * vara {@code @RestController} för att Spring ska hitta webb-lagret, men
 * allt under den är alltså vanlig Java.
 */
@Configuration
public class AppConfig {

    /**
     * Väljer och bygger den {@link AiClient} appen faktiskt pratar med.
     * Bara en av leverantörerna behöver någonsin instansieras - ingen
     * annan kod vill ha den overksamma - så det är ett vanligt if/else,
     * inte något som behöver lösas upp med {@code @Primary}/{@code @Qualifier}.
     * Defaultar till Anthropic; sätt egenskapen {@code ai.provider} (env var
     * {@code AI_PROVIDER}) till "openai" för att byta tillbaka.
     */
    @Bean
    public AiClient aiClient(
            @Value("${ai.provider:anthropic}") String provider,
            @Value("${ANTHROPIC_API_KEY:}") String anthropicApiKey,
            @Value("${OPENAI_API_KEY:}") String openAiApiKey) {
        return "openai".equalsIgnoreCase(provider)
                ? new OpenAiClient(openAiApiKey)
                : new AnthropicChatClient(anthropicApiKey);
    }

    @Bean
    public SessionArtifactStore sessionArtifactStore() {
        return new FileSessionArtifactStore();
    }

    @Bean
    public BackgroundSessionUpdater backgroundSessionUpdater(
            AiClient aiClient, SessionArtifactStore sessionArtifactStore) {
        return new BackgroundSessionUpdater(aiClient, sessionArtifactStore);
    }

    @Bean
    public PsykologenService psykologenService(
            AiClient aiClient,
            SessionArtifactStore sessionArtifactStore,
            BackgroundSessionUpdater backgroundSessionUpdater) {
        return new PsykologenService(aiClient, sessionArtifactStore, backgroundSessionUpdater);
    }
}
