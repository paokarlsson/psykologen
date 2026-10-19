package se.olaslab.psykologen;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.olaslab.psykologen.auth.AuthController;
import se.olaslab.psykologen.auth.AuthProperties;
import se.olaslab.psykologen.auth.InvalidUsersException;
import se.olaslab.psykologen.auth.LoginAttemptTracker;
import se.olaslab.psykologen.auth.PasswordHashRunner;
import se.olaslab.psykologen.auth.SecurityConfig;
import se.olaslab.psykologen.controller.PsykologenController;
import se.olaslab.psykologen.prompt.PromptStore;
import se.olaslab.psykologen.service.PsykologenService;
import se.olaslab.psykologen.service.SessionArtifactUpdater;
import se.olaslab.psykologen.service.UserSessionRegistry;
import se.olaslab.psykologen.service.ai.AiClient;
import se.olaslab.psykologen.service.ai.AnthropicChatClient;
import se.olaslab.psykologen.service.ai.OpenAiClient;
import se.olaslab.psykologen.storage.FileSessionArtifactStore;
import se.olaslab.psykologen.storage.SessionArtifactStore;
import spola.config.Config;
import spola.config.ConfigException;
import spola.config.Source;
import spola.http.HttpServer;
import spola.http.HttpServerOptions;
import spola.json.JsonObject;
import spola.security.web.WebSecurity;
import spola.util.Logging;
import spola.util.LoggingSettings;
import spola.web.ForwardedHeaders;
import spola.web.Responses;
import spola.web.Router;
import spola.web.Sessions;
import spola.web.TrustedProxies;

public final class PsykologenApplication {

    /** Miljövariablerna appen läser. Konton läses också, från APP_AUTH_USERS_<n>_*. */
    private static final Set<String> ENVIRONMENT = Set.of("AI_PROVIDER", "ANTHROPIC_API_KEY", "OPENAI_API_KEY",
            "APP_STORAGE_BASE_DIR", "COOKIE_SECURE", "TRUSTED_PROXIES");
    private static final String USERS = "APP_AUTH_USERS_";

    private static final Map<String, String> DEFAULTS = Map.of(
            "AI_PROVIDER", "anthropic",
            "APP_STORAGE_BASE_DIR", "data/users",
            // Sätt COOKIE_SECURE=true när appen ligger bakom TLS. compose.prod.yaml gör det åt dig.
            "COOKIE_SECURE", "false");

    private PsykologenApplication() {
    }

    public static void main(String[] args) throws IOException {
        Logging.configure(LoggingSettings.defaults());

        var password = PasswordHashRunner.requestedPassword(args);
        if (password.isPresent()) {
            PasswordHashRunner.print(password.get(), SecurityConfig.passwordHasher(), System.out);
            return;
        }

        HttpServer server;
        try {
            Config config = config(args, System.getenv(), Path.of(".env"));
            server = HttpServer.start(serverOptions(), router(config, buildSessionRegistry(config),
                    InstantSource.system()));
        } catch (MissingApiKeyException e) {
            failToStart(e.getEnvVar() + " är inte satt, så Psykologen-backend kan inte prata med "
                            + e.getProvider() + ".",
                    "Sätt din nyckel innan du startar backend, t.ex.:\n"
                            + "  - Skapa backend/.env med raden " + e.getEnvVar() + "=din_nyckel_här, eller\n"
                            + "  - export " + e.getEnvVar() + "=din_nyckel_här i terminalen du startar backend från.\n"
                            + "Eller byt leverantör: sätt AI_PROVIDER=openai / AI_PROVIDER=anthropic.");
            return;
        } catch (InvalidUsersException e) {
            failToStart("Inloggningen kan inte startas: " + e.getMessage(),
                    "Lägg upp minst ett konto innan du startar backend:\n"
                            + "  1. Skapa en lösenordshash:\n"
                            + "     java Build.java run --hash-password=ditt_lösenord\n"
                            + "  2. Lägg in kontot i backend/.env (en rad per fält, numrerade från 0):\n"
                            + "     APP_AUTH_USERS_0_USERNAME=ditt_användarnamn\n"
                            + "     APP_AUTH_USERS_0_PASSWORDHASH={bcrypt}$2a$10$...\n"
                            + "Användarnamnet blir också namnet på användarens lagringskatalog, därför\n"
                            + "är bara 3-32 tecken med gemener, siffror, - och _ tillåtna.");
            return;
        } catch (ConfigException e) {
            failToStart(e.getMessage(), "Rätta backend/.env, miljövariablerna eller argumenten och starta igen.");
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    }

    /**
     * Konfigurationen. En senare källa vinner: standardvärdena, {@code envFile} ({@code .env} i
     * katalogen appen startas från: {@code backend/.env} lokalt, {@code /app/.env} i containern),
     * miljövariablerna och sist argumenten ({@code --AI_PROVIDER=openai}).
     *
     * <p>{@code .env} läses som en properties-fil: citattecken runt ett värde blir en del av
     * värdet, och {@code export} framför en rad fungerar inte.
     */
    public static Config config(String[] args, Map<String, String> environment, Path envFile) {
        Map<String, String> read = new TreeMap<>();
        environment.forEach((name, value) -> {
            if (ENVIRONMENT.contains(name) || name.startsWith(USERS)) {
                read.put(name, value);
            }
        });
        return Config.builder()
                .add(Source.map("standardvärden", DEFAULTS))
                .add(Source.optionalFile(envFile))
                .add(Source.map("miljövariabler", read))
                .add(Source.arguments(args))
                .sensitive(key -> key.endsWith("_API_KEY") || key.endsWith("_PASSWORDHASH"))
                .build();
    }

    /** Port 8080 på alla adresser: Caddy i en annan container ska nå appen. */
    public static HttpServerOptions serverOptions() {
        return HttpServerOptions.DEFAULT.withAddress(InetAddress.ofLiteral("0.0.0.0")).withPort(8080);
    }

    /**
     * Hela appen: proxyhuvudena först, sedan säkerheten (som också sköter sessionerna och
     * CSRF), och sist inloggningen och samtals-API:t.
     */
    public static Router router(Config config, UserSessionRegistry registry, InstantSource clock) {
        AuthProperties authProperties = AuthProperties.fromConfig(config);
        authProperties.validate();
        boolean secure = config.bind("", c -> c.booleanValue("COOKIE_SECURE"));

        Sessions sessions = SecurityConfig.sessions(secure, clock);
        WebSecurity security = SecurityConfig.security(sessions, secure);
        var routes = Router.builder()
                // Utan detta ser appen inte att användaren surfar på https bakom proxyn.
                .filter(ForwardedHeaders.trusting(trustedProxies(config)))
                .filter(security.filter())
                .get("/actuator/health", request -> Responses.json(request, 200,
                        JsonObject.builder().put("status", "UP").build()));
        new AuthController(SecurityConfig.authenticator(authProperties, SecurityConfig.passwordHasher()),
                new LoginAttemptTracker()).routes(routes);
        new PsykologenController(registry).routes(routes);
        return routes.build();
    }

    /**
     * Proxyerna vars {@code X-Forwarded-Proto} och {@code X-Forwarded-For} appen litar på. Utan
     * TRUSTED_PROXIES är det de privata näten, som Spring Boot (Tomcats {@code RemoteIpValve})
     * litade på. Med en fast adress för Caddy kan det snävas in: {@code TRUSTED_PROXIES=172.30.0.10}.
     */
    private static TrustedProxies trustedProxies(Config config) {
        return config.get("TRUSTED_PROXIES")
                .filter(value -> !value.isBlank())
                .map(value -> TrustedProxies.of(value.split("\\s*,\\s*")))
                .orElseGet(TrustedProxies::privateNetworks);
    }

    private static UserSessionRegistry buildSessionRegistry(Config config) {
        AiClient aiClient = buildAiClient(config);
        // Samma pool bär både föranropen som turen väntar in och jobben som körs efteråt.
        ExecutorService executor = Executors.newCachedThreadPool();
        Path usersDir = config.bind("", c -> c.path("APP_STORAGE_BASE_DIR"));

        return new UserSessionRegistry(
                username -> buildPsykologenService(aiClient, executor, usersDir.resolve(username)));
    }

    public static PsykologenService buildPsykologenService(AiClient aiClient, ExecutorService executor,
            Path userDir) {
        SessionArtifactStore artifactStore = new FileSessionArtifactStore(userDir);
        PromptStore promptStore = new PromptStore(userDir);
        SessionArtifactUpdater artifactUpdater =
                new SessionArtifactUpdater(aiClient, artifactStore, promptStore, executor);
        return new PsykologenService(aiClient, artifactStore, artifactUpdater, promptStore, executor);
    }

    private static AiClient buildAiClient(Config config) {
        String provider = config.get("AI_PROVIDER").orElse("anthropic");
        AiClient client = "openai".equalsIgnoreCase(provider)
                ? new OpenAiClient(config.get("OPENAI_API_KEY").orElse(""))
                : new AnthropicChatClient(config.get("ANTHROPIC_API_KEY").orElse(""));

        if (!client.isConfigured()) {
            throw new MissingApiKeyException(client.providerName(), client.requiredEnvVar());
        }
        return client;
    }

    private static void failToStart(String description, String action) {
        System.err.printf("%n***************************%nAPPEN KUNDE INTE STARTA%n***************************%n%n"
                + "Beskrivning:%n%n%s%n%nÅtgärd:%n%n%s%n%n", description, action);
        System.exit(1);
    }
}
