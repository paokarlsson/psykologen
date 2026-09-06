package se.olaslab.psykologen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import se.olaslab.psykologen.prompt.PromptStore;
import se.olaslab.psykologen.prompt.PromptTemplates;
import se.olaslab.psykologen.service.ai.AiClient;
import se.olaslab.psykologen.service.ai.AiResponse;
import se.olaslab.psykologen.session.ChatMessage;
import se.olaslab.psykologen.session.ConversationSession;
import se.olaslab.psykologen.session.Role;
import se.olaslab.psykologen.storage.HistoryEntry;
import se.olaslab.psykologen.storage.SessionArtifactStore;

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

        // Ett nystartat samtal ska inte ärva profil/plan från en tidigare körning.
        artifactStore.clear();
    }

    public synchronized void resetSession() {
        this.session = new ConversationSession(promptStore.getSystemPrompt());
        artifactStore.clear();
    }

    public Map<String, Object> getPromptSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("useCustomPrompts", promptStore.isUseCustomPrompts());
        result.put("prompts", promptStore.currentValues());
        result.put("defaults", PromptStore.defaults());
        result.put("sessionDurationMinutes", promptStore.getSessionDurationMinutes());
        result.put("defaultSessionDurationMinutes", PromptStore.DEFAULT_SESSION_DURATION_MINUTES);
        return result;
    }

    public void setSessionDurationMinutes(double minutes) {
        promptStore.setSessionDurationMinutes(minutes);
    }

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

    public List<HistoryEntry> getHistory() {
        return artifactStore.readHistory();
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
