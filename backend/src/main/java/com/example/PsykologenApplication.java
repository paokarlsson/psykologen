package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.Environment;

import com.example.prompt.PromptStore;
import com.example.service.BackgroundSessionUpdater;
import com.example.service.PsykologenService;
import com.example.service.ai.AiClient;
import com.example.service.ai.AnthropicChatClient;
import com.example.service.ai.OpenAiClient;
import com.example.storage.FileSessionArtifactStore;
import com.example.storage.SessionArtifactStore;

/**
 * Startpunkt - och samtidigt appens composition root. Hela objektgrafen
 * under {@link com.example.controller.PsykologenController} byggs här med
 * vanlig {@code new}, utan någon {@code @Configuration}/{@code @Bean}: bara
 * {@link PsykologenService} registreras som Spring-bean, eftersom det är
 * det enda controllern faktiskt behöver ha injicerat. Allt annat
 * ({@link AiClient}, {@link SessionArtifactStore}, {@link BackgroundSessionUpdater})
 * är lokala variabler i {@link #buildPsykologenService}, aldrig kända av
 * Spring - de är medvetet ramverksfria klasser utan
 * {@code @Component}/{@code @Autowired}/{@code @Value} på sig själva.
 */
@SpringBootApplication
public class PsykologenApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PsykologenApplication.class);
        app.addInitializers(context -> {
            GenericApplicationContext ctx = (GenericApplicationContext) context;
            ctx.registerBean(PsykologenService.class, () -> buildPsykologenService(ctx.getEnvironment()));
        });
        app.run(args);
    }

    private static PsykologenService buildPsykologenService(Environment env) {
        AiClient aiClient = buildAiClient(env);
        SessionArtifactStore artifactStore = new FileSessionArtifactStore();
        PromptStore promptStore = new PromptStore();
        BackgroundSessionUpdater backgroundUpdater = new BackgroundSessionUpdater(aiClient, artifactStore, promptStore);
        return new PsykologenService(aiClient, artifactStore, backgroundUpdater, promptStore);
    }

    /**
     * Väljer och bygger den {@link AiClient} appen faktiskt pratar med.
     * Defaultar till Anthropic; sätt egenskapen {@code ai.provider} (env
     * var {@code AI_PROVIDER}) till "openai" för att byta tillbaka.
     */
    private static AiClient buildAiClient(Environment env) {
        String provider = env.getProperty("ai.provider", "anthropic");
        return "openai".equalsIgnoreCase(provider)
                ? new OpenAiClient(env.getProperty("OPENAI_API_KEY", ""))
                : new AnthropicChatClient(env.getProperty("ANTHROPIC_API_KEY", ""));
    }
}
