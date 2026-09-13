package se.olaslab.psykologen.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import se.olaslab.psykologen.session.ChatMessage;
import se.olaslab.psykologen.session.ConversationSession;
import se.olaslab.psykologen.session.Role;
import se.olaslab.psykologen.storage.HistoryEntry;
import se.olaslab.psykologen.storage.SessionArtifactStore;

class ContextStrategyTest {

    /** Session med systemprompt, öppning och tre utbyten - sista användarrepliken obesvarad. */
    private static ConversationSession sessionMedSamtal() {
        ConversationSession session = new ConversationSession("Du är Erik.");
        session.addAssistantMessage("Hej, hur mår du?");
        for (int i = 1; i <= 3; i++) {
            session.addUserMessage("fråga " + i);
            session.addAssistantMessage("svar " + i);
        }
        session.addUserMessage("senaste");
        return session;
    }

    private static String innehall(List<ChatMessage> messages, int index) {
        return messages.get(index).content();
    }

    @Test
    void fullHistorikGerSammaSomTidigareBeteende() {
        ConversationSession session = sessionMedSamtal();
        SessionArtifactStore store = new TomStore();

        List<ChatMessage> byggd = new FullHistoryStrategy().build(session, store);

        assertEquals(session.historyBeforeLastMessage(), byggd);
        // Systemprompt + öppning + tre utbyten = 8 meddelanden, senaste användarrepliken bortklippt.
        assertEquals(8, byggd.size());
    }

    @Test
    void fonstretBehallerSystempromptenOchDeSenasteReplikerna() {
        ConversationSession session = sessionMedSamtal();

        List<ChatMessage> byggd = new SlidingWindowStrategy().build(session, new TomStore());

        assertEquals(Role.SYSTEM, byggd.getFirst().role());
        assertEquals("Du är Erik.", innehall(byggd, 0));
        // Systemprompt + sex repliker.
        assertEquals(7, byggd.size());
        assertEquals("svar 3", byggd.getLast().content());
        // Öppningsrepliken har fallit ur fönstret.
        assertTrue(byggd.stream().noneMatch(m -> "Hej, hur mår du?".equals(m.content())));
    }

    @Test
    void profilstrateginErsatterHistorikenMedProfilen() {
        ConversationSession session = sessionMedSamtal();

        List<ChatMessage> byggd = new ProfileStrategy().build(session, new StoreMedProfil("Patienten sover dåligt."));

        // Systemprompt, profil som systemmeddelande, fyra repliker.
        assertEquals(6, byggd.size());
        assertEquals(Role.SYSTEM, byggd.get(0).role());
        assertEquals(Role.SYSTEM, byggd.get(1).role());
        assertTrue(innehall(byggd, 1).contains("Patienten sover dåligt."));
        assertEquals("fråga 2", innehall(byggd, 2));
        assertEquals("svar 3", byggd.getLast().content());
    }

    @Test
    void profilstrateginKlararEnTomProfil() {
        ConversationSession session = sessionMedSamtal();

        List<ChatMessage> byggd = new ProfileStrategy().build(session, new TomStore());

        // Inget tomt profilblock läggs in - bara systemprompten och replikerna.
        assertEquals(5, byggd.size());
        assertEquals(Role.SYSTEM, byggd.getFirst().role());
        assertEquals(Role.USER, byggd.get(1).role());
    }

    @Test
    void okantIdFallerTillbakaPaStandard() {
        assertSame(ContextStrategies.byId(FullHistoryStrategy.ID).getClass(),
                ContextStrategies.byId("finns-inte").getClass());
        assertTrue(!ContextStrategies.exists("finns-inte"));
        assertEquals(FullHistoryStrategy.ID, ContextStrategies.DEFAULT_ID);
    }

    private static class TomStore implements SessionArtifactStore {

        @Override
        public Optional<String> readProfile() {
            return Optional.empty();
        }

        @Override
        public Optional<String> readPlan() {
            return Optional.empty();
        }

        @Override
        public void writeProfile(String content) {
        }

        @Override
        public void writePlan(String content) {
        }

        @Override
        public void appendHistory(HistoryEntry entry) {
        }

        @Override
        public List<HistoryEntry> readHistory() {
            return List.of();
        }

        @Override
        public void clear() {
        }
    }

    private static final class StoreMedProfil extends TomStore {

        private final String profil;

        StoreMedProfil(String profil) {
            this.profil = profil;
        }

        @Override
        public Optional<String> readProfile() {
            return Optional.of(profil);
        }
    }
}
