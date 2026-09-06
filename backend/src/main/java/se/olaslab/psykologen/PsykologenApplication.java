package se.olaslab.psykologen;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.Environment;

import se.olaslab.psykologen.auth.AuthProperties;
import se.olaslab.psykologen.auth.LoginAttemptTracker;
import se.olaslab.psykologen.auth.PasswordHashRunner;
import se.olaslab.psykologen.prompt.PromptStore;
import se.olaslab.psykologen.service.BackgroundSessionUpdater;
import se.olaslab.psykologen.service.PsykologenService;
import se.olaslab.psykologen.service.UserSessionRegistry;
import se.olaslab.psykologen.service.ai.AiClient;
import se.olaslab.psykologen.service.ai.AnthropicChatClient;
import se.olaslab.psykologen.service.ai.OpenAiClient;
import se.olaslab.psykologen.storage.FileSessionArtifactStore;
import se.olaslab.psykologen.storage.SessionArtifactStore;

@SpringBootApplication
public class PsykologenApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PsykologenApplication.class);
        app.addInitializers(context -> ((GenericApplicationContext) context).register(new ApplicationBeans()));
        app.run(args);
    }

    static class ApplicationBeans implements BeanRegistrar {

        @Override
        public void register(BeanRegistry registry, Environment env) {
            registry.registerBean(UserSessionRegistry.class,
                    spec -> spec.supplier(context -> buildSessionRegistry(env)));
            registry.registerBean(AuthProperties.class,
                    spec -> spec.supplier(context -> AuthProperties.fromEnvironment(env)));
            registry.registerBean(LoginAttemptTracker.class);
            registry.registerBean(PasswordHashRunner.class);
        }
    }

    private static UserSessionRegistry buildSessionRegistry(Environment env) {
        AiClient aiClient = buildAiClient(env);
        ExecutorService backgroundExecutor = Executors.newCachedThreadPool();
        Path usersDir = Path.of(env.getProperty("app.storage.base-dir", "data/users"));

        return new UserSessionRegistry(
                username -> buildPsykologenService(aiClient, backgroundExecutor, usersDir.resolve(username)));
    }

    private static PsykologenService buildPsykologenService(AiClient aiClient, ExecutorService backgroundExecutor,
            Path userDir) {
        SessionArtifactStore artifactStore = new FileSessionArtifactStore(userDir);
        PromptStore promptStore = new PromptStore(userDir);
        BackgroundSessionUpdater backgroundUpdater =
                new BackgroundSessionUpdater(aiClient, artifactStore, promptStore, backgroundExecutor);
        return new PsykologenService(aiClient, artifactStore, backgroundUpdater, promptStore);
    }

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
