package se.olaslab.psykologen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import se.olaslab.psykologen.context.ContextStrategies;
import se.olaslab.psykologen.context.ContextStrategy;
import se.olaslab.psykologen.prompt.ChangelogResponse;
import se.olaslab.psykologen.prompt.PromptStore;
import se.olaslab.psykologen.prompt.PromptTemplates;
import se.olaslab.psykologen.service.ai.AiClient;
import se.olaslab.psykologen.service.ai.AiResponse;
import se.olaslab.psykologen.session.ChatMessage;
import se.olaslab.psykologen.session.ConversationSession;
import se.olaslab.psykologen.session.Role;
import se.olaslab.psykologen.storage.HistoryEntry;
import se.olaslab.psykologen.storage.SessionArtifactStore;
import se.olaslab.psykologen.trace.LlmCall;
import se.olaslab.psykologen.trace.TraceSummary;

public class PsykologenService {

    private final AiClient aiClient;
    private final SessionArtifactStore artifactStore;
    private final SessionArtifactUpdater artifactUpdater;
    private final PromptStore promptStore;
    private final ExecutorService executor;
    private volatile ConversationSession session;

    public PsykologenService(AiClient aiClient, SessionArtifactStore artifactStore,
            SessionArtifactUpdater artifactUpdater, PromptStore promptStore, ExecutorService executor) {
        this.aiClient = aiClient;
        this.artifactStore = artifactStore;
        this.artifactUpdater = artifactUpdater;
        this.promptStore = promptStore;
        this.executor = executor;
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
        result.put("contextStrategy", promptStore.getContextStrategy());
        result.put("contextStrategies", ContextStrategies.all().stream()
                .map(strategy -> Map.of(
                        "id", strategy.id(),
                        "label", strategy.label(),
                        "description", strategy.description()))
                .toList());
        return result;
    }

    public void setContextStrategy(String id) {
        promptStore.setContextStrategy(id);
    }

    private ContextStrategy contextStrategy() {
        return ContextStrategies.byId(promptStore.getContextStrategy());
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

        AiResponse openingResponse = session.tracer()
                .record(LlmCall.OPPNING, session.turn(), openingMessages, aiClient::chat);
        session.recordUsage(openingResponse);

        String agentOpening = openingResponse.text();
        session.addAssistantMessage(agentOpening);
        return agentOpening;
    }

    public String processMessage(String userInput) throws Exception {
        ConversationSession current = session;
        String previousResponse = current.lastAgentMessage();

        current.addUserMessage(userInput);
        // Räknas upp först, så att turnumret i traceen gäller den tur anropen tillhör.
        current.incrementConversationCount();

        // Föranropen är oberoende av varandra och går parallellt. Turen tar därför ungefär
        // lika lång tid som förr, men Erik svarar på en profil som redan känner till det
        // patienten just sa - i stället för gårdagens bild, en tur försenad.
        Future<?> profileUpdate = executor.submit(
                () -> artifactUpdater.updateProfile(current, previousResponse, userInput));
        reflectOnInput(userInput);
        awaitPreparation(profileUpdate);

        String agentResponse = respondAsErik(userInput);
        current.addAssistantMessage(agentResponse);

        // Planen är trögare än profilen och hinner bli klar medan användaren läser svaret.
        artifactUpdater.triggerPlanUpdate(current, userInput, agentResponse);

        return agentResponse;
    }

    /** Väntar in ett föranrop. Uppgiften hanterar och spelar in sina egna fel - turen rullar vidare. */
    private void awaitPreparation(Future<?> preparation) {
        try {
            preparation.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // Ska inte hända: uppgiften sväljer sina egna fel. Skulle den ändå kasta får
            // det inte fälla turen - Erik svarar på de dokument som redan finns.
        }
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

    public List<LlmCall> getTrace() {
        return session.tracer().calls();
    }

    public TraceSummary getTraceSummary() {
        return session.tracer().summary();
    }

    /** Eriks tysta inre reflektioner - ackumulerade men aldrig tidigare synliga. */
    public List<String> getThoughts() {
        return session.thoughts();
    }

    private void reflectOnInput(String userInput) throws Exception {
        String prompt = PromptTemplates.thoughtReflection(
                promptStore.getThoughtReflectionTemplate(), userInput, session.thoughtsAsBulletText());

        List<ChatMessage> request = new ArrayList<>(contextStrategy().build(session, artifactStore));
        request.add(ChatMessage.instruction(Role.USER, prompt));

        AiResponse response = session.tracer()
                .record(LlmCall.REFLEKTION, session.turn(), request, aiClient::chat);
        session.recordUsage(response);

        ChangelogResponse parsed = ChangelogResponse.parse(response.text().trim());
        if (parsed.changelog() == null) {
            // Ingen ändringslogg i svaret: en egen mall kör det gamla formatet, där
            // svaret bara är de nya tankarna. Då gäller det gamla beteendet.
            session.addThoughtLines(parsed.document());
            return;
        }

        session.replaceThoughts(parsed.document());
        if (!parsed.hasNoChange()) {
            artifactStore.appendHistory(
                    HistoryEntry.now(HistoryEntry.THOUGHTS, session.elapsedMinutes(), parsed.changelog()));
        }
    }

    private String respondAsErik(String userInput) throws Exception {
        String sessionPlan = artifactStore.readPlan().orElse("");
        String patientProfile = artifactStore.readProfile().orElse("");
        String prompt = PromptTemplates.erikResponse(promptStore.getErikResponseTemplate(),
                session.thoughtsAsBulletText(), patientProfile, sessionPlan, session.elapsedMinutes(),
                promptStore.getSessionDurationMinutes(), userInput);

        List<ChatMessage> request = new ArrayList<>(contextStrategy().build(session, artifactStore));
        request.add(ChatMessage.instruction(Role.USER, prompt));

        AiResponse response = session.tracer()
                .record(LlmCall.SVAR, session.turn(), request, aiClient::chat);
        session.recordUsage(response);
        return response.text();
    }
}
