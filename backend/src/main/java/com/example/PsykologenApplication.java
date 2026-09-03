package com.example;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.Environment;

import com.example.prompt.PromptStore;
import com.example.service.BackgroundSessionUpdater;
import com.example.service.PsykologenService;
import com.example.service.UserSessionRegistry;
import com.example.service.ai.AiClient;
import com.example.service.ai.AnthropicChatClient;
import com.example.service.ai.OpenAiClient;
import com.example.storage.FileSessionArtifactStore;
import com.example.storage.SessionArtifactStore;

/**
 * Startpunkt - och samtidigt appens composition root. Hela objektgrafen
 * under {@link com.example.controller.PsykologenController} byggs här med
 * vanlig {@code new}, utan någon {@code @Configuration}/{@code @Bean}: bara
 * {@link UserSessionRegistry} registreras som Spring-bean, eftersom det är
 * det enda controllern faktiskt behöver ha injicerat. Allt annat
 * ({@link AiClient}, {@link SessionArtifactStore}, {@link BackgroundSessionUpdater})
 * är lokala variabler i {@link #buildPsykologenService}, aldrig kända av
 * Spring - de är medvetet ramverksfria klasser utan
 * {@code @Component}/{@code @Autowired}/{@code @Value} på sig själva.
 *
 * Undantaget från den regeln är {@link com.example.auth.SecurityConfig}, som
 * är en riktig {@code @Configuration}. Spring Securitys filterkedja byggs via
 * en {@code HttpSecurity}-builder som ramverket självt äger, och att tvinga in
 * den här hade gjort konfigurationen svårare att läsa än den vinst det gav.
 * Autentisering är dessutom ett ramverksansvar från början, till skillnad från
 * samtalslogiken ovan.
 *
 * {@link AiClient} och trådpoolen är tillståndslösa respektive delbara och
 * byggs därför en gång och delas av alla användare, medan varje användare får
 * en egen {@link PsykologenService} via {@link UserSessionRegistry}.
 */
@SpringBootApplication
public class PsykologenApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PsykologenApplication.class);
        app.addInitializers(context -> {
            GenericApplicationContext ctx = (GenericApplicationContext) context;
            ctx.registerBean(UserSessionRegistry.class, () -> buildSessionRegistry(ctx.getEnvironment()));
        });
        app.run(args);
    }

    private static UserSessionRegistry buildSessionRegistry(Environment env) {
        AiClient aiClient = buildAiClient(env);
        ExecutorService backgroundExecutor = Executors.newCachedThreadPool();
        Path usersDir = Path.of(env.getProperty("app.storage.base-dir", "data/users"));

        return new UserSessionRegistry(
                username -> buildPsykologenService(aiClient, backgroundExecutor, usersDir.resolve(username)));
    }

    /**
     * Bygger en användares service. {@code userDir} är användarens egen
     * katalog - all lagring (profil, plan, historik, promptar) hamnar där, så
     * två inloggade användare aldrig delar filer. Användarnamnet har
     * validerats av {@link com.example.auth.AuthProperties} vid uppstart, så
     * det kan inte innehålla katalogtraversering.
     */
    private static PsykologenService buildPsykologenService(AiClient aiClient, ExecutorService backgroundExecutor,
            Path userDir) {
        SessionArtifactStore artifactStore = new FileSessionArtifactStore(userDir);
        PromptStore promptStore = new PromptStore(userDir);
        BackgroundSessionUpdater backgroundUpdater =
                new BackgroundSessionUpdater(aiClient, artifactStore, promptStore, backgroundExecutor);
        return new PsykologenService(aiClient, artifactStore, backgroundUpdater, promptStore);
    }

    /**
     * Väljer och bygger den {@link AiClient} appen faktiskt pratar med.
     * Defaultar till Anthropic; sätt egenskapen {@code ai.provider} (env
     * var {@code AI_PROVIDER}) till "openai" för att byta tillbaka.
     *
     * Att nyckeln saknas upptäcks här och inte i {@link PsykologenService},
     * eftersom servicen numera skapas först när en användare loggar in.
     * Kontrollen här körs vid uppstart, så {@link MissingApiKeyFailureAnalyzer}
     * hinner ge sitt läsbara startfel innan någon ens öppnar appen.
     */
    private static AiClient buildAiClient(Environment env) {
        String provider = env.getProperty("ai.provider", "anthropic");
        AiClient client = "openai".equalsIgnoreCase(provider)
                ? new OpenAiClient(env.getProperty("OPENAI_API_KEY", ""))
                : new AnthropicChatClient(env.getProperty("ANTHROPIC_API_KEY", ""));

        if (!client.isConfigured()) {
            throw new MissingApiKeyException(client.providerName(), client.requiredEnvVar());
        }
        return client;
    }
}
