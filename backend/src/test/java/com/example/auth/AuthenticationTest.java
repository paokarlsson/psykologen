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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthenticationTest.TestBeans.class)
class AuthenticationTest {

    private static final String ANNA_PASSWORD = "annas-hemliga-losen";
    private static final String BO_PASSWORD = "bosses-hemliga-losen";

    private static final Path STORAGE_DIR = createTempStorageDir();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        registry.add("APP_AUTH_USERS_0_USERNAME", () -> "anna");
        registry.add("APP_AUTH_USERS_0_PASSWORDHASH", () -> "{bcrypt}" + encoder.encode(ANNA_PASSWORD));
        registry.add("APP_AUTH_USERS_1_USERNAME", () -> "bosse");
        registry.add("APP_AUTH_USERS_1_PASSWORDHASH", () -> "{bcrypt}" + encoder.encode(BO_PASSWORD));
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

    @Test
    void inloggningFungerarUtanBefintligSession() throws Exception {
        // Regression: changeSessionId() kastar när ingen session finns.
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

    @Test
    void anvandareSerInteVarandrasProfil() throws Exception {
        MockHttpSession annasSession = login("anna", ANNA_PASSWORD);
        MockHttpSession bosSession = login("bosse", BO_PASSWORD);

        // Katalogen finns först efter inloggning: PsykologenService tömmer den när
        // den skapas, så en profil skriven innan dess vore borta.
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

        @Bean
        AuthProperties authProperties(Environment environment) {
            return AuthProperties.fromEnvironment(environment);
        }

        @Bean
        LoginAttemptTracker loginAttemptTracker() {
            return new LoginAttemptTracker();
        }

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
