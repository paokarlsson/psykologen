package com.example.service;

import com.example.SystemPrompts;
import com.google.gson.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PsykologenService {
    private static final String MODEL = "gpt-4o-mini";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final Gson gson = new Gson();
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    
    @Value("${openai.api.key:#{systemProperties['OPENAI_API_KEY']}}")
    private String apiKey;
    
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
    
    public void initializeSession() {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY must be set");
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
    
    private JsonObject callOpenAI(List<Map<String, Object>> conversationMessages) throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", MODEL);
        
        JsonArray messagesArray = new JsonArray();
        for (Map<String, Object> msg : conversationMessages) {
            JsonObject msgNode = new JsonObject();
            msgNode.addProperty("role", (String) msg.get("role"));
            msgNode.addProperty("content", (String) msg.get("content"));
            messagesArray.add(msgNode);
        }
        requestBody.add("messages", messagesArray);
        
        String requestBodyStr = gson.toJson(requestBody);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyStr))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API error: " + response.statusCode() + " " + response.body());
        }
        
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    
    private String getAssistantResponse(JsonObject apiResponse) {
        return apiResponse.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }
    
    private void updateTokenCount(JsonObject apiResponse) {
        JsonElement usage = apiResponse.get("usage");
        if (usage != null && !usage.isJsonNull()) {
            JsonObject usageObj = usage.getAsJsonObject();
            totalInputTokens += usageObj.get("prompt_tokens").getAsInt();
            totalOutputTokens += usageObj.get("completion_tokens").getAsInt();
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
        
        JsonObject openingResponse = callOpenAI(openingMessages);
        String agentOpening = getAssistantResponse(openingResponse);
        updateTokenCount(openingResponse);
        
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
        
        JsonObject thoughtResponse = callOpenAI(thoughtMessages);
        updateTokenCount(thoughtResponse);
        
        return getAssistantResponse(thoughtResponse).trim();
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
        
        JsonObject response = callOpenAI(responseMessages);
        updateTokenCount(response);
        
        return getAssistantResponse(response);
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
            
            JsonObject response = callOpenAI(List.of(profileMessage));
            String updatedProfile = getAssistantResponse(response);
            
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
            
            JsonObject response = callOpenAI(List.of(planMessage));
            String updatedPlan = getAssistantResponse(response);
            
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