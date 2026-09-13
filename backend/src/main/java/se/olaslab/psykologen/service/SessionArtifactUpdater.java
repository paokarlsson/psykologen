package se.olaslab.psykologen.service;

import java.util.List;
import java.util.concurrent.ExecutorService;

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

/**
 * Håller profilen och planen uppdaterade vid sidan av samtalet.
 *
 * <p>De två dokumenten har olika brådska. Profilen är det Erik behöver för att svara på det som
 * just sades och skrivs därför före svaret, medan planen är trögare och får skrivas färdigt i
 * bakgrunden medan användaren läser.
 */
public class SessionArtifactUpdater {

    private final AiClient aiClient;
    private final SessionArtifactStore artifactStore;
    private final PromptStore promptStore;
    private final ExecutorService executor;

    public SessionArtifactUpdater(AiClient aiClient, SessionArtifactStore artifactStore,
            PromptStore promptStore, ExecutorService executor) {
        this.aiClient = aiClient;
        this.artifactStore = artifactStore;
        this.promptStore = promptStore;
        this.executor = executor;
    }

    /**
     * Skriver in patientens senaste replik i profilen. Blockerande - anroparen kör den på egen
     * tråd parallellt med de andra föranropen och väntar in den innan Erik svarar.
     *
     * <p>Utdraget paras om jämfört med hur det såg ut när profilen uppdaterades efter svaret:
     * Eriks föregående replik plus patientens nya. Ingenting tappas, utbytet delas bara på
     * mitten - och det som bär nya fakta om patienten är med innan Erik öppnar munnen.
     */
    public void updateProfile(ConversationSession session, String previousResponse, String userInput) {
        try {
            String existingProfile = artifactStore.readProfile().orElse("");
            String prompt = PromptTemplates.profileUpdate(
                    promptStore.getProfileUpdateTemplate(), existingProfile, previousResponse, userInput);

            List<ChatMessage> request = List.of(ChatMessage.instruction(Role.USER, prompt));
            AiResponse response = session.tracer()
                    .record(LlmCall.PROFIL, session.turn(), request, aiClient::chat);
            ChangelogResponse parsed = ChangelogResponse.parse(response.text());
            artifactStore.writeProfile(parsed.document());
            logChange(HistoryEntry.PROFILE, session, parsed);
        } catch (Exception e) {
            // Ett misslyckat föranrop får aldrig störa samtalet - Erik svarar då på den profil
            // som redan fanns. Felet är inte tappat: TraceRecorder har redan spelat in det,
            // så det syns i Glaslådan.
        }
    }

    /**
     * Håller listan över öppna trådar aktuell. Blockerande, av samma skäl som profilen: Erik ska
     * kunna återkomma till en tråd i den replik han skriver nu.
     *
     * <p>Med hela historiken i kontexten finns trådarna redan där, i teorin - men det som nämndes
     * i förbifarten tjugo repliker bort drunknar. En egen lista är det en skicklig terapeut för i
     * huvudet, och den är kort nog att faktiskt läsas.
     */
    public void updateOpenThreads(ConversationSession session, String previousResponse, String userInput) {
        try {
            String existingThreads = artifactStore.readOpenThreads().orElse("");
            String prompt = PromptTemplates.threadsUpdate(
                    promptStore.getThreadsUpdateTemplate(), existingThreads, previousResponse, userInput);

            List<ChatMessage> request = List.of(ChatMessage.instruction(Role.USER, prompt));
            AiResponse response = session.tracer()
                    .record(LlmCall.TRADAR, session.turn(), request, aiClient::chat);
            ChangelogResponse parsed = ChangelogResponse.parse(response.text());
            artifactStore.writeOpenThreads(parsed.document());
            logChange(HistoryEntry.THREADS, session, parsed);
        } catch (Exception e) {
            // Som profilen: ett misslyckat föranrop får inte störa samtalet, och felet
            // finns redan inspelat i traceen.
        }
    }

    public void triggerPlanUpdate(ConversationSession session, String userInput, String agentResponse) {
        executor.submit(() -> updatePlan(session, userInput, agentResponse));
    }

    private void updatePlan(ConversationSession session, String userInput, String agentResponse) {
        try {
            String existingPlan = artifactStore.readPlan().orElse("");
            String timingAnalysis = TimingAnalyzer.describe(session.messages());

            String prompt = PromptTemplates.planUpdate(promptStore.getPlanUpdateTemplate(),
                    existingPlan, userInput, agentResponse, timingAnalysis, session.elapsedMinutes(),
                    promptStore.getSessionDurationMinutes());

            List<ChatMessage> request = List.of(ChatMessage.instruction(Role.USER, prompt));
            AiResponse response = session.tracer()
                    .record(LlmCall.PLAN, session.turn(), request, aiClient::chat);
            ChangelogResponse parsed = ChangelogResponse.parse(response.text());
            artifactStore.writePlan(parsed.document());
            logChange(HistoryEntry.PLAN, session, parsed);
        } catch (Exception e) {
            // Ett misslyckat bakgrundsjobb får aldrig störa samtalet. Felet är inte tappat:
            // TraceRecorder har redan spelat in det, så det syns i Glaslådan.
        }
    }

    private void logChange(String type, ConversationSession session, ChangelogResponse parsed) {
        if (parsed.hasNoChange()) {
            return;
        }
        artifactStore.appendHistory(HistoryEntry.now(type, session.elapsedMinutes(), parsed.changelog()));
    }
}
