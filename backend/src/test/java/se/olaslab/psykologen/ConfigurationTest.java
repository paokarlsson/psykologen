package se.olaslab.psykologen;

import static spola.test.Assertions.assertEquals;
import static spola.test.Assertions.assertThrows;
import static spola.test.Assertions.assertTrue;
import static spola.test.Tests.suite;
import static spola.test.Tests.test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;

import se.olaslab.psykologen.auth.AuthProperties;
import se.olaslab.psykologen.auth.PasswordHashRunner;
import se.olaslab.psykologen.auth.SecurityConfig;
import se.olaslab.psykologen.service.UserSessionRegistry;
import spola.config.Config;
import spola.config.ConfigException;
import spola.security.AuthResult;
import spola.test.Suite;

/** backend/.env, miljövariablerna och {@code --hash-password}. */
public final class ConfigurationTest {

    private ConfigurationTest() {
    }

    public static Suite tests() {
        return suite("ConfigurationTest",
                test("envFilensBcryptHashLasesOforandrad", ConfigurationTest::envFilensBcryptHashLasesOforandrad),
                test("miljovariablerVinnerOverFilenOchArgumentOverBada", ConfigurationTest::miljovariablerVinnerOverFilenOchArgumentOverBada),
                test("standardvardenUtanFil", ConfigurationTest::standardvardenUtanFil),
                test("andraMiljovariablerLasesInte", ConfigurationTest::andraMiljovariablerLasesInte),
                test("felaktigCookieSecureStopparStarten", ConfigurationTest::felaktigCookieSecureStopparStarten),
                test("hashPasswordSkriverEnHashSomGarAttLoggaInMed", ConfigurationTest::hashPasswordSkriverEnHashSomGarAttLoggaInMed),
                test("hashPasswordUtanLosenordBerOmEtt", ConfigurationTest::hashPasswordUtanLosenordBerOmEtt));
    }

    static void envFilensBcryptHashLasesOforandrad() throws Exception {
        String hash = SecurityConfig.passwordHasher().hash("hemligt");
        Path dir = Files.createTempDirectory("psykologen-env");
        Path env = dir.resolve(".env");
        try {
            Files.writeString(env, """
                    # Kopiera till backend/.env.
                    AI_PROVIDER=anthropic
                    ANTHROPIC_API_KEY=sk-ant-test
                    APP_AUTH_USERS_0_USERNAME=anna
                    APP_AUTH_USERS_0_PASSWORDHASH=%s
                    """.formatted(hash));

            Config config = PsykologenApplication.config(new String[0], Map.of(), env);
            AuthProperties users = AuthProperties.fromConfig(config);
            users.validate();

            assertEquals(hash, users.getUsers().getFirst().passwordHash());
            var authenticator = SecurityConfig.authenticator(users, SecurityConfig.passwordHasher());
            assertTrue(authenticator.authenticate("anna", "hemligt") instanceof AuthResult.Authenticated);
            assertEquals(Optional.of("sk-ant-test"), config.get("ANTHROPIC_API_KEY"));
            // Nyckeln och hashen maskeras när konfigurationen skrivs ut.
            assertTrue(config.isSensitive("ANTHROPIC_API_KEY"));
            assertTrue(config.isSensitive("APP_AUTH_USERS_0_PASSWORDHASH"));
        } finally {
            Files.deleteIfExists(env);
            Files.deleteIfExists(dir);
        }
    }

    static void miljovariablerVinnerOverFilenOchArgumentOverBada() throws Exception {
        Path dir = Files.createTempDirectory("psykologen-env");
        Path env = dir.resolve(".env");
        try {
            Files.writeString(env, "AI_PROVIDER=openai\nCOOKIE_SECURE=false\n");

            Config config = PsykologenApplication.config(new String[] {"--COOKIE_SECURE=true"},
                    Map.of("AI_PROVIDER", "anthropic", "COOKIE_SECURE", "false"), env);

            assertEquals(Optional.of("anthropic"), config.get("AI_PROVIDER"));
            assertEquals(Optional.of("true"), config.get("COOKIE_SECURE"));
        } finally {
            Files.deleteIfExists(env);
            Files.deleteIfExists(dir);
        }
    }

    static void standardvardenUtanFil() {
        Config config = PsykologenApplication.config(new String[0], Map.of(), Path.of("finns-inte.env"));

        assertEquals(Optional.of("anthropic"), config.get("AI_PROVIDER"));
        assertEquals(Optional.of("data/users"), config.get("APP_STORAGE_BASE_DIR"));
        assertEquals(Optional.of("false"), config.get("COOKIE_SECURE"));
    }

    static void andraMiljovariablerLasesInte() {
        Config config = PsykologenApplication.config(new String[0],
                Map.of("PATH", "/usr/bin", "APP_AUTH_USERS_0_USERNAME", "anna"), Path.of("finns-inte.env"));

        assertEquals(Optional.empty(), config.get("PATH"));
        assertEquals(Optional.of("anna"), config.get("APP_AUTH_USERS_0_USERNAME"));
    }

    static void felaktigCookieSecureStopparStarten() {
        Config config = PsykologenApplication.config(new String[0], Map.of(
                "COOKIE_SECURE", "ja",
                "APP_AUTH_USERS_0_USERNAME", "anna",
                "APP_AUTH_USERS_0_PASSWORDHASH", SecurityConfig.passwordHasher().hash("hemligt")),
                Path.of("finns-inte.env"));

        assertThrows(ConfigException.class, () -> PsykologenApplication.router(config,
                new UserSessionRegistry(username -> null), InstantSource.system()));
    }

    static void hashPasswordSkriverEnHashSomGarAttLoggaInMed() {
        String[] args = {"--hash-password=mitt lösenord"};
        assertEquals(Optional.of("mitt lösenord"), PasswordHashRunner.requestedPassword(args));

        String output = print("mitt lösenord");

        String line = output.lines().filter(l -> l.startsWith("APP_AUTH_USERS_0_PASSWORDHASH=")).findFirst().orElseThrow();
        String hash = line.substring(line.indexOf('=') + 1);
        assertTrue(hash.startsWith("{bcrypt}$2a$10$"), hash);
        assertTrue(SecurityConfig.passwordHasher().matches("mitt lösenord", hash));
    }

    static void hashPasswordUtanLosenordBerOmEtt() {
        assertEquals(Optional.of(""), PasswordHashRunner.requestedPassword(new String[] {"--hash-password"}));
        assertEquals(Optional.empty(), PasswordHashRunner.requestedPassword(new String[] {"--annat=1"}));

        assertEquals("Ange ett lösenord: --hash-password=ditt_lösenord", print("").strip());
    }

    private static String print(String password) {
        var bytes = new ByteArrayOutputStream();
        PasswordHashRunner.print(password, SecurityConfig.passwordHasher(), new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
