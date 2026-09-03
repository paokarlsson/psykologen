package com.example.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.example.prompt.PromptStore;
import com.example.service.BackgroundSessionUpdater;
import com.example.service.PsykologenService;
import com.example.service.UserSessionRegistry;
import com.example.service.ai.AiClient;
import com.example.service.ai.AiResponse;
import com.example.session.ChatMessage;
import com.example.storage.FileSessionArtifactStore;
import com.example.storage.SessionArtifactStore;

/**
 * Att API:t faktiskt kräver inloggning, och att två inloggade användare inte
 * kommer åt varandras samtalsdata.
 *
 * Testet bygger sitt eget {@link UserSessionRegistry} med en fejkad
 * {@link AiClient} och en temporär katalog. Det går tack vare att
 * {@link PsykologenService} och dess samarbetare är ramverksfria klasser utan
 * annoteringar - ingen produktionskod behöver ändras för att kunna testas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthenticationTest.TestBeans.class)
class AuthenticationTest {

    private static final String ANNA_PASSWORD = "annas-hemliga-losen";
    private static final String BO_PASSWORD = "bosses-hemliga-losen";

    private static final Path STORAGE_DIR = createTempStorageDir();

    @Autowired
    private MockMvc mockMvc;

    /**
     * Hasharna räknas fram här i stället för att hårdkodas, så testet
     * verifierar samma väg som produktionen: lösenord in, BCrypt-hash i
     * konfigurationen, verifiering vid inloggning.
     */
    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        registry.add("app.auth.users[0].username", () -> "anna");
        registry.add("app.auth.users[0].password-hash", () -> "{bcrypt}" + encoder.encode(ANNA_PASSWORD));
        registry.add("app.auth.users[1].username", () -> "bosse");
        registry.add("app.auth.users[1].password-hash", () -> "{bcrypt}" + encoder.encode(BO_PASSWORD));
    }

    @Test
    void skyddadEndpointKraverInloggning() throws Exception {
        mockMvc.perform(get("/api/psykologen/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void felLosenordGerUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content("{\"username\":\"anna\",\"password\":\"fel-losen\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Första besöket har ingen session ännu - inloggningen måste skapa en, inte
     * förutsätta att den finns. (Regression: {@code changeSessionId()} kastar
     * {@code IllegalStateException} när ingen session finns, vilket gjorde att
     * varje förstagångsinloggning från en webbläsare misslyckades.)
     */
    @Test
    void inloggningFungerarUtanBefintligSession() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content("{\"username\":\"anna\",\"password\":\"" + ANNA_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("anna"));
    }

    @Test
    void rattLosenordGerInloggadSession() throws Exception {
        MockHttpSession session = login("anna", ANNA_PASSWORD);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("anna"));
    }

    /**
     * Kärnan i hela ändringen: profil, plan och samtal hör till en enskild
     * användare. Går det här testet sönder är användarseparationen borta,
     * oavsett om inloggningen fortfarande fungerar.
     */
    @Test
    void anvandareSerInteVarandrasProfil() throws Exception {
        MockHttpSession annasSession = login("anna", ANNA_PASSWORD);
        MockHttpSession bosSession = login("bosse", BO_PASSWORD);

        // Först efter inloggning finns användarens katalog - PsykologenService
        // tömmer den när den skapas, så en profil skriven innan dess vore borta.
        mockMvc.perform(get("/api/psykologen/profile").session(annasSession)).andExpect(status().isOk());
        mockMvc.perform(get("/api/psykologen/profile").session(bosSession)).andExpect(status().isOk());

        Files.writeString(STORAGE_DIR.resolve("anna").resolve("profile.md"), "Annas privata profil");

        mockMvc.perform(get("/api/psykologen/profile").session(annasSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").value("Annas privata profil"));

        mockMvc.perform(get("/api/psykologen/profile").session(bosSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").value("Ingen profil skapad än."));
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());
        return session;
    }

    private static Path createTempStorageDir() {
        try {
            return Files.createTempDirectory("psykologen-test");
        } catch (IOException e) {
            throw new IllegalStateException("Kunde inte skapa temporär testkatalog", e);
        }
    }

    @AfterAll
    static void removeTempStorage() throws IOException {
        if (!Files.exists(STORAGE_DIR)) {
            return;
        }
        try (var paths = Files.walk(STORAGE_DIR)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    // Städning får inte fälla testet
                }
            });
        }
    }

    @TestConfiguration
    static class TestBeans {

        /**
         * Samma objektgraf som {@link com.example.PsykologenApplication} bygger,
         * men mot en temporär katalog och utan riktiga AI-anrop. Registret
         * registreras där via en initializer i {@code main()}, som inte körs
         * under test - därför byggs det här.
         */
        @Bean
        UserSessionRegistry userSessionRegistry() {
            AiClient aiClient = new FakeAiClient();
            ExecutorService executor = Executors.newSingleThreadExecutor();

            return new UserSessionRegistry(username -> {
                Path userDir = STORAGE_DIR.resolve(username);
                SessionArtifactStore artifactStore = new FileSessionArtifactStore(userDir);
                PromptStore promptStore = new PromptStore(userDir);
                return new PsykologenService(aiClient, artifactStore,
                        new BackgroundSessionUpdater(aiClient, artifactStore, promptStore, executor), promptStore);
            });
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
            return new AiResponse("Fejkat svar", 0, 0);
        }
    }
}
