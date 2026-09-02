package com.example.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.MissingApiKeyException;
import com.example.prompt.PromptStore;
import com.example.prompt.PromptTemplates;
import com.example.service.ai.AiClient;
import com.example.service.ai.AiResponse;
import com.example.session.ChatMessage;
import com.example.session.ConversationSession;
import com.example.session.Role;
import com.example.storage.SessionArtifactStore;

/**
 * Orkestrerar ett terapisamtal: pratar med {@link AiClient} för varje tur,
 * håller reda på tillståndet via en {@link ConversationSession}, och
 * triggar bakgrundsuppdatering av profil/plan efter varje meddelande.
 *
 * En vanlig, ramverksfri klass - all koppling till Spring sker i
 * {@link com.example.PsykologenApplication}. Att API-nyckeln saknas upptäcks
 * redan här i konstruktorn (istället för i en separat
 * {@code @PostConstruct}-metod), så bean-skapandet failar direkt om
 * leverantören inte är konfigurerad.
 */
public class PsykologenService {

    private final AiClient aiClient;
    private final SessionArtifactStore artifactStore;
    private final BackgroundSessionUpdater backgroundUpdater;
    private final PromptStore promptStore;
    private volatile ConversationSession session;

    public PsykologenService(AiClient aiClient, SessionArtifactStore artifactStore,
            BackgroundSessionUpdater backgroundUpdater, PromptStore promptStore) {
        this.aiClient = aiClient;
        this.artifactStore = artifactStore;
        this.backgroundUpdater = backgroundUpdater;
        this.promptStore = promptStore;
        this.session = new ConversationSession(promptStore.getSystemPrompt());

        artifactStore.clear();

        if (!aiClient.isConfigured()) {
            throw new MissingApiKeyException(aiClient.providerName(), aiClient.requiredEnvVar());
        }
    }

    /**
     * Startar om samtalet helt blankt: ny historik (med aktuell systemprompt),
     * nollställd klocka och tankar, och tömd profil/plan. Rör inte sparade
     * promptinställningar - bara själva samtalet.
     */
    public synchronized void resetSession() {
        this.session = new ConversationSession(promptStore.getSystemPrompt());
        artifactStore.clear();
    }

    /** Aktuella promptvärden + standardvärden + på/av-läge + sessionslängd, för GUI:t. */
    public Map<String, Object> getPromptSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("useCustomPrompts", promptStore.isUseCustomPrompts());
        result.put("prompts", promptStore.currentValues());
        result.put("defaults", PromptStore.defaults());
        result.put("sessionDurationMinutes", promptStore.getSessionDurationMinutes());
        result.put("defaultSessionDurationMinutes", PromptStore.DEFAULT_SESSION_DURATION_MINUTES);
        return result;
    }

    /** Hur många minuter Erik ska planera samtalet mot (default 45). */
    public void setSessionDurationMinutes(double minutes) {
        promptStore.setSessionDurationMinutes(minutes);
    }

    /** Sparar egna texter för en eller flera promptnycklar (slår samtidigt på användningen av dem). */
    public void updatePrompts(Map<String, String> updates) {
        promptStore.update(updates);
    }

    public void resetPrompt(String key) {
        promptStore.resetToDefault(key);
    }

    public void resetAllPrompts() {
        promptStore.resetAllToDefault();
    }

    public void setCustomPromptsEnabled(boolean enabled) {
        promptStore.setUseCustomPrompts(enabled);
    }

    public String startConversation() throws Exception {
        List<ChatMessage> openingMessages = new ArrayList<>(session.messages());
        openingMessages.add(ChatMessage.instruction(Role.USER, promptStore.getOpeningInstruction()));

        AiResponse openingResponse = aiClient.chat(openingMessages);
        session.recordUsage(openingResponse);

        String agentOpening = openingResponse.text();
        session.addAssistantMessage(agentOpening);
        return agentOpening;
    }

    public String processMessage(String userInput) throws Exception {
        session.addUserMessage(userInput);

        String newThoughts = reflectOnInput(userInput);
        session.addThoughtLines(newThoughts);

        String agentResponse = respondAsErik(userInput);
        session.addAssistantMessage(agentResponse);
        session.incrementConversationCount();

        backgroundUpdater.triggerUpdates(session, userInput, agentResponse);

        return agentResponse;
    }

    public String getProfile() {
        return artifactStore.readProfile().orElse("Ingen profil skapad än.");
    }

    public String getPlan() {
        return artifactStore.readPlan()
                .filter(plan -> !plan.isEmpty())
                .orElse("Ingen plan skapad än.");
    }

    public List<ChatMessage> getConversation() {
        return session.messages();
    }

    private String reflectOnInput(String userInput) throws Exception {
        String prompt = PromptTemplates.thoughtReflection(
                promptStore.getThoughtReflectionTemplate(), userInput, session.thoughtsAsBulletText());

        List<ChatMessage> request = new ArrayList<>(session.historyBeforeLastMessage());
        request.add(ChatMessage.instruction(Role.USER, prompt));

        AiResponse response = aiClient.chat(request);
        session.recordUsage(response);
        return response.text().trim();
    }

    private String respondAsErik(String userInput) throws Exception {
        String sessionPlan = artifactStore.readPlan().orElse("");
        String prompt = PromptTemplates.erikResponse(promptStore.getErikResponseTemplate(),
                session.thoughtsAsBulletText(), sessionPlan, session.elapsedMinutes(),
                promptStore.getSessionDurationMinutes(), userInput);

        List<ChatMessage> request = new ArrayList<>(session.historyBeforeLastMessage());
        request.add(ChatMessage.instruction(Role.USER, prompt));

        AiResponse response = aiClient.chat(request);
        session.recordUsage(response);
        return response.text();
    }
}
