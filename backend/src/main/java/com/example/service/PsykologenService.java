package com.example.service;

import java.util.ArrayList;
import java.util.List;

import com.example.MissingApiKeyException;
import com.example.SystemPrompts;
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
    private final ConversationSession session;

    public PsykologenService(AiClient aiClient, SessionArtifactStore artifactStore,
            BackgroundSessionUpdater backgroundUpdater) {
        this.aiClient = aiClient;
        this.artifactStore = artifactStore;
        this.backgroundUpdater = backgroundUpdater;
        this.session = new ConversationSession(SystemPrompts.SYSTEM_PROMPT);

        artifactStore.clear();

        if (!aiClient.isConfigured()) {
            throw new MissingApiKeyException(aiClient.providerName(), aiClient.requiredEnvVar());
        }
    }

    public String startConversation() throws Exception {
        List<ChatMessage> openingMessages = new ArrayList<>(session.messages());
        openingMessages.add(ChatMessage.instruction(Role.USER, PromptTemplates.OPENING_INSTRUCTION));

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
        String prompt = PromptTemplates.thoughtReflection(userInput, session.thoughtsAsBulletText());

        List<ChatMessage> request = new ArrayList<>(session.historyBeforeLastMessage());
        request.add(ChatMessage.instruction(Role.USER, prompt));

        AiResponse response = aiClient.chat(request);
        session.recordUsage(response);
        return response.text().trim();
    }

    private String respondAsErik(String userInput) throws Exception {
        String sessionPlan = artifactStore.readPlan().orElse("");
        String prompt = PromptTemplates.erikResponse(
                session.thoughtsAsBulletText(), sessionPlan, session.elapsedMinutes(), userInput);

        List<ChatMessage> request = new ArrayList<>(session.historyBeforeLastMessage());
        request.add(ChatMessage.instruction(Role.USER, prompt));

        AiResponse response = aiClient.chat(request);
        session.recordUsage(response);
        return response.text();
    }
}
