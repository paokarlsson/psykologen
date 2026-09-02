package com.example.service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.prompt.PromptStore;
import com.example.prompt.PromptTemplates;
import com.example.service.ai.AiClient;
import com.example.service.ai.AiResponse;
import com.example.session.ChatMessage;
import com.example.session.ConversationSession;
import com.example.session.Role;
import com.example.storage.SessionArtifactStore;

/**
 * Uppdaterar patient-profilen och sessionsplanen i bakgrunden efter varje
 * meddelande, utan att blockera svaret till frontend. Fel sväljs tyst
 * (precis som tidigare) - ett misslyckat bakgrundsjobb ska aldrig störa
 * det pågående samtalet.
 */
public class BackgroundSessionUpdater {

    private final AiClient aiClient;
    private final SessionArtifactStore artifactStore;
    private final PromptStore promptStore;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public BackgroundSessionUpdater(AiClient aiClient, SessionArtifactStore artifactStore, PromptStore promptStore) {
        this.aiClient = aiClient;
        this.artifactStore = artifactStore;
        this.promptStore = promptStore;
    }

    public void triggerUpdates(ConversationSession session, String userInput, String agentResponse) {
        executor.submit(() -> updateProfile(userInput, agentResponse));
        executor.submit(() -> updatePlan(session, userInput, agentResponse));
    }

    private void updateProfile(String userInput, String agentResponse) {
        try {
            String existingProfile = artifactStore.readProfile().orElse("");
            String prompt = PromptTemplates.profileUpdate(
                    promptStore.getProfileUpdateTemplate(), existingProfile, userInput, agentResponse);

            AiResponse response = aiClient.chat(List.of(ChatMessage.instruction(Role.USER, prompt)));
            artifactStore.writeProfile(response.text());
        } catch (Exception e) {
            // Tyst felhantering, precis som tidigare
        }
    }

    private void updatePlan(ConversationSession session, String userInput, String agentResponse) {
        try {
            String existingPlan = artifactStore.readPlan().orElse("");
            String timingAnalysis = TimingAnalyzer.describe(session.messages());

            String prompt = PromptTemplates.planUpdate(promptStore.getPlanUpdateTemplate(),
                    existingPlan, userInput, agentResponse, timingAnalysis, session.elapsedMinutes(),
                    promptStore.getSessionDurationMinutes());

            AiResponse response = aiClient.chat(List.of(ChatMessage.instruction(Role.USER, prompt)));
            artifactStore.writePlan(response.text());
        } catch (Exception e) {
            // Tyst felhantering, precis som tidigare
        }
    }
}
