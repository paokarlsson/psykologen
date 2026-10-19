package se.olaslab.psykologen;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.olaslab.psykologen.auth.SecurityConfig;
import se.olaslab.psykologen.service.UserSessionRegistry;
import se.olaslab.psykologen.service.ai.AiClient;
import se.olaslab.psykologen.service.ai.AiResponse;
import se.olaslab.psykologen.session.ChatMessage;
import spola.config.Config;
import spola.http.HttpServer;
import spola.http.HttpServerOptions;

/**
 * Hela appen på en ledig port, med två konton och en AI-klient som svarar "Fejkat svar":
 * motsvarigheten till {@code @SpringBootTest} med en riktig server.
 */
public final class TestApp implements AutoCloseable {

    public static final String ANNA_PASSWORD = "annas-hemliga-losen";
    public static final String BO_PASSWORD = "bosses-hemliga-losen";

    private final Path storageDir;
    private final ExecutorService executor;
    private final HttpServer server;

    private TestApp(Path storageDir, ExecutorService executor, HttpServer server) {
        this.storageDir = storageDir;
        this.executor = executor;
        this.server = server;
    }

    public static TestApp start() throws IOException {
        Path storageDir = Files.createTempDirectory("psykologen-test");
        ExecutorService executor = Executors.newCachedThreadPool();
        var hasher = SecurityConfig.passwordHasher();
        Config config = PsykologenApplication.config(new String[0], Map.of(
                "APP_AUTH_USERS_0_USERNAME", "anna",
                "APP_AUTH_USERS_0_PASSWORDHASH", hasher.hash(ANNA_PASSWORD),
                "APP_AUTH_USERS_1_USERNAME", "bosse",
                "APP_AUTH_USERS_1_PASSWORDHASH", hasher.hash(BO_PASSWORD)),
                storageDir.resolve("finns-inte.env"));

        AiClient aiClient = new FakeAiClient();
        // Föranropen körs parallellt med varandra, precis som i appen.
        var registry = new UserSessionRegistry(username ->
                PsykologenApplication.buildPsykologenService(aiClient, executor, storageDir.resolve(username)));

        HttpServer server = HttpServer.start(HttpServerOptions.DEFAULT.withPort(0),
                PsykologenApplication.router(config, registry, InstantSource.system()));
        return new TestApp(storageDir, executor, server);
    }

    public URI base() {
        return URI.create("http://127.0.0.1:" + server.port());
    }

    public Browser browser() {
        return new Browser(base());
    }

    public Path storageDir() {
        return storageDir;
    }

    @Override
    public void close() throws IOException {
        server.close();
        executor.shutdownNow();
        try (var paths = Files.walk(storageDir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class FakeAiClient implements AiClient {

        @Override
        public String providerName() {
            return "Fake";
        }

        @Override
        public String requiredEnvVar() {
            return "FAKE_API_KEY";
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public AiResponse chat(List<ChatMessage> messages) {
            return new AiResponse("Fejkat svar", "fake-modell", 0, 0, 0, 0);
        }
    }
}
