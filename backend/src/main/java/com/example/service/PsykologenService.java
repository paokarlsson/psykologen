package com.example.service;

import com.example.MissingApiKeyException;
import com.example.SystemPrompts;
import com.example.service.ai.AiClient;
import com.example.service.ai.AiResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PsykologenService {

    @Autowired
    private AiClient aiClient;

    private final List<Map<String, Object>> messages;
    private final List<String> internalThoughts;
    private final long sessionStartTime;
    private final ExecutorService executor;

    private int conversationCount = 0;
    private int totalInputTokens = 0;
    private int totalOutputTokens = 0;

    public PsykologenService() {
        this.messages = new ArrayList<>();
        this.internalThoughts = new ArrayList<>();
        this.sessionStartTime = System.currentTimeMillis();
        this.executor = Executors.newCachedThreadPool();

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", SystemPrompts.SYSTEM_PROMPT);
        messages.add(systemMessage);

        cleanupSessionFiles();
    }

    @PostConstruct
    private void validateApiKey() {
        if (!aiClient.isConfigured()) {
            throw new MissingApiKeyException(aiClient.providerName(), aiClient.requiredEnvVar());
        }
    }

    public void initializeSession() {
        if (!aiClient.isConfigured()) {
            throw new IllegalStateException(aiClient.requiredEnvVar() + " must be set");
        }
    }

    private void cleanupSessionFiles() {
        try {
            Files.deleteIfExists(Paths.get("profile.md"));
            Files.deleteIfExists(Paths.get("plan.md"));
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    public String startConversation() throws Exception {
        initializeSession();

        // Erik opens the conversation
        Map<String, Object> openingPrompt = new HashMap<>();
        openingPrompt.put("role", "user");
        openingPrompt.put("content", "Starta samtalet som du själv, Erik. Detta är vårt första möte. Håll det kort.");

        List<Map<String, Object>> openingMessages = new ArrayList<>(messages);
        openingMessages.add(openingPrompt);

        AiResponse openingResponse = aiClient.chat(openingMessages);
        String agentOpening = openingResponse.text();
        totalInputTokens += openingResponse.inputTokens();
        totalOutputTokens += openingResponse.outputTokens();

        Map<String, Object> assistantMessage = new HashMap<>();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("content", agentOpening);
        assistantMessage.put("timestamp", System.currentTimeMillis());
        assistantMessage.put("session_time", System.currentTimeMillis() - sessionStartTime);
        messages.add(assistantMessage);

        return agentOpening;
    }

    public String processMessage(String userInput) throws Exception {
        initializeSession();

        long currentTime = System.currentTimeMillis();
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userInput);
        userMessage.put("timestamp", currentTime);
        userMessage.put("session_time", currentTime - sessionStartTime);
        messages.add(userMessage);

        String newThoughts = processInternalThoughts(userInput);
        if (newThoughts != null && !newThoughts.startsWith("Inga")) {
            String[] thoughtLines = newThoughts.split("\n");
            for (String line : thoughtLines) {
                line = line.trim();
                if (line.startsWith("- ")) {
                    internalThoughts.add(line.substring(2));
                } else if (!line.isEmpty() && !line.startsWith("-")) {
                    internalThoughts.add(line);
                }
            }
        }

        String agentResponse = getErikResponse(userInput);

        long responseTime = System.currentTimeMillis();
        Map<String, Object> assistantMessage = new HashMap<>();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("content", agentResponse);
        assistantMessage.put("timestamp", responseTime);
        assistantMessage.put("session_time", responseTime - sessionStartTime);
        messages.add(assistantMessage);

        conversationCount++;

        // Start background updates
        startBackgroundUpdates(userInput, agentResponse);

        return agentResponse;
    }

    private String processInternalThoughts(String userInput) throws Exception {
        String currentThoughts = String.join("\n", internalThoughts.stream()
                .map(thought -> "- " + thought)
                .toList());

        String thoughtPrompt = String.format("""
            Baserat på vad användaren precis sa: "%s"

            Dina nuvarande inre reflektion:
            %s

            Uppdatera dina inre psykologiska reflektion. Lägg till nya observationer, hypoteser eller insikter. Skriv bara de NYA tankarna du får, inte alla gamla.

            Skriv bara dina nya inre tankar, en per rad med bindestreck.
            """, userInput, currentThoughts.isEmpty() ? "Inga tidigare tankar." : currentThoughts);

        Map<String, Object> thoughtMessage = new HashMap<>();
        thoughtMessage.put("role", "user");
        thoughtMessage.put("content", thoughtPrompt);

        List<Map<String, Object>> thoughtMessages = new ArrayList<>(messages.subList(0, messages.size() - 1));
        thoughtMessages.add(thoughtMessage);

        AiResponse thoughtResponse = aiClient.chat(thoughtMessages);
        totalInputTokens += thoughtResponse.inputTokens();
        totalOutputTokens += thoughtResponse.outputTokens();

        return thoughtResponse.text().trim();
    }

    private String getErikResponse(String userInput) throws Exception {
        String currentThoughtsStr = String.join("\n", internalThoughts.stream()
                .map(thought -> "- " + thought)
                .toList());

        String sessionPlan = readSessionPlan();

        double sessionTimeMinutes = (System.currentTimeMillis() - sessionStartTime) / 60000.0;

        String responsePrompt = String.format("""
            Du har tillgång till:

            DINA INRE REFLEKTION:
            %s

            SESSIONSPLAN (följ denna strategiskt):
            %s

            SESSIONSTID:
            - Pågått: %.1f minuter

            Användarens senaste meddelande: "%s"

            Som professionell terapeut ska du:
            - Fortsätta samtalet i din egen takt
            - Anpassa samtalet efter patientens behov
            - Avsluta endast när det känns naturligt eller om patienten vill avsluta

            Svara nu högt som Erik psykologen. Håll svaret kort (1-2 meningar).
            """, currentThoughtsStr, sessionPlan.isEmpty() ? "Ingen plan än." : sessionPlan,
            sessionTimeMinutes, userInput);

        Map<String, Object> responseMessage = new HashMap<>();
        responseMessage.put("role", "user");
        responseMessage.put("content", responsePrompt);

        List<Map<String, Object>> responseMessages = new ArrayList<>(messages.subList(0, messages.size() - 1));
        responseMessages.add(responseMessage);

        AiResponse response = aiClient.chat(responseMessages);
        totalInputTokens += response.inputTokens();
        totalOutputTokens += response.outputTokens();

        return response.text();
    }

    private String readSessionPlan() {
        try {
            Path planPath = Paths.get("plan.md");
            if (Files.exists(planPath)) {
                return Files.readString(planPath);
            }
        } catch (IOException e) {
            // Ignore read errors
        }
        return "";
    }

    public String getProfile() {
        try {
            Path profilePath = Paths.get("profile.md");
            if (Files.exists(profilePath)) {
                return Files.readString(profilePath);
            }
        } catch (IOException e) {
            // Ignore read errors
        }
        return "Ingen profil skapad än.";
    }

    public String getPlan() {
        return readSessionPlan().isEmpty() ? "Ingen plan skapad än." : readSessionPlan();
    }

    public List<Map<String, Object>> getConversation() {
        return new ArrayList<>(messages);
    }


    private void startBackgroundUpdates(String userInput, String agentResponse) {
        // Start profile update in background
        executor.submit(() -> updateProfileBackground(userInput, agentResponse));

        // Start session plan update in background
        executor.submit(() -> updateSessionPlan(userInput, agentResponse));
    }

    private void updateProfileBackground(String userInput, String agentResponse) {
        try {
            String profileContent = "";
            Path profilePath = Paths.get("profile.md");
            if (Files.exists(profilePath)) {
                profileContent = Files.readString(profilePath);
            }

            String profilePrompt = String.format("""
                Du är en profil-analytiker som samlar fakta om PATIENTEN från ett psykologsamtal.

                BEFINTLIG PATIENT-PROFIL:
                %s

                NYTT SAMTALSUTDRAG:
                Patient: %s
                Psykolog Erik: %s

                Uppdatera ENDAST profilen för PATIENTEN med NYA FAKTA som framkommer. Inkludera:
                - Personliga detaljer om patienten (ålder, jobb, familj, etc.)
                - Patientens intressen och hobbies
                - Patientens problem eller utmaningar
                - Patientens mål och drömmar
                - Patientens personlighet och beteende

                VIKTIGT: Samla endast information om PATIENTEN, inte om psykologen Erik.

                Skriv en uppdaterad patient-profil i markdown-format med tydlig struktur:

                # PATIENT-PROFIL

                ## Grundläggande Information
                [personliga detaljer]

                ## Problem & Utmaningar
                [vad patienten söker hjälp för]

                ## Personlighet & Beteende
                [observationer om patienten]

                ## Mål & Drömmar
                [vad patienten vill uppnå]

                ## Övriga Noteringar
                [andra relevanta fakta]
                """, profileContent.isEmpty() ? "Ingen befintlig profil." : profileContent,
                userInput, agentResponse);

            Map<String, Object> profileMessage = new HashMap<>();
            profileMessage.put("role", "user");
            profileMessage.put("content", profilePrompt);

            AiResponse response = aiClient.chat(List.of(profileMessage));
            String updatedProfile = response.text();

            Files.writeString(profilePath, updatedProfile);

        } catch (Exception e) {
            // Silent error handling
        }
    }

    private void updateSessionPlan(String userInput, String agentResponse) {
        try {
            String planContent = "";
            Path planPath = Paths.get("plan.md");
            if (Files.exists(planPath)) {
                planContent = Files.readString(planPath);
            }

            double elapsedTime = (System.currentTimeMillis() - sessionStartTime) / 60000.0;

            String timingAnalysis = buildTimingAnalysis();

            String planPrompt = String.format("""
                Du är en expert psykolog som skapar adaptiva terapeutiska sessionsplaner.

                BEFINTLIG SESSIONSPLAN:
                %s

                SENASTE SAMTALSUTBYTE:
                Patient: %s
                Erik: %s

                %s

                SESSIONSSTATUS:
                - Tid förfluten: %.1f minuter

                BEDÖM PROGRESSIONEN: Analysera tidsstämplarna ovan och bedöm:
                - Hur snabbt går samtalet framåt?
                - Ger patienten djupa svar eller korta/ytliga?
                - Hur mycket tid tar varje utbyte?
                - Är patienten engagerad eller motsträvig?
                - Behöver vi ändra takt eller fokus?

                INSTRUKTIONER FÖR PLANREVISION:
                - Prioritera de VIKTIGASTE punkterna först
                - Om progression är långsam: korta ner planen, fokusera på 1-2 huvudpunkter
                - Om tid börjar ta slut: anpassa "Nästa Steg" för snabb avslutning
                - Var realistisk om vad som hinns med

                Format i markdown:

                # SESSIONSPLAN

                ## Identifierade Problem
                [huvudproblem som framkommit - prioriterat]

                ## Fokusområden (Justerat för tid)
                [vad som MÅSTE utforskas inom kvarvarande tid]

                ## Terapeutisk Approach
                [snabba, effektiva tekniker för kort session]

                ## Nästa Steg (Tidsjusterat)
                [konkreta frågor som hinns med - prioriterade]

                ## Sessionsmål (Reviderat)
                [realistiska mål för kvarvarande tid]

                ## Anteckningar för Erik
                [specifika råd: prioritera, korta ner, eller förbereda avslutning]

                VIKTIGT: Anpassa hela planen baserat på progression och tid kvar!
                """, planContent.isEmpty() ? "Ingen befintlig plan." : planContent,
                userInput, agentResponse, timingAnalysis, elapsedTime, 10 - elapsedTime);

            Map<String, Object> planMessage = new HashMap<>();
            planMessage.put("role", "user");
            planMessage.put("content", planPrompt);

            AiResponse response = aiClient.chat(List.of(planMessage));
            String updatedPlan = response.text();

            Files.writeString(planPath, updatedPlan);

        } catch (Exception e) {
            // Silent error handling
        }
    }

    private String buildTimingAnalysis() {
        StringBuilder analysis = new StringBuilder("SAMTALSHISTORIK MED TIDSSTÄMPLAR:\n");

        for (int i = 1; i < messages.size(); i++) { // Skip system message
            Map<String, Object> msg = messages.get(i);
            if (msg.containsKey("session_time")) {
                String role = "user".equals(msg.get("role")) ? "Patient" : "Erik";
                double sessionMins = ((Number) msg.get("session_time")).doubleValue() / 60000.0;
                String content = (String) msg.get("content");
                String preview = content.length() > 50 ? content.substring(0, 50) + "..." : content;
                analysis.append(String.format("%d. [%.1fmin] %s: %s\n", i, sessionMins, role, preview));
            }
        }

        return analysis.toString();
    }
}
