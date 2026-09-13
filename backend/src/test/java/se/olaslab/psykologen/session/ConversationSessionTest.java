package se.olaslab.psykologen.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConversationSessionTest {

    private static ConversationSession sessionMedTankar() {
        ConversationSession session = new ConversationSession("Du är Erik.");
        session.addThoughtLines("- sover dåligt\n- verkar stressad");
        return session;
    }

    @Test
    void revideringErsatterHelaTankelistan() {
        ConversationSession session = sessionMedTankar();

        session.replaceThoughts("- verkar stressad, troligen jobbet\n- nämnde brodern i förbifarten");

        assertEquals(List.of("verkar stressad, troligen jobbet", "nämnde brodern i förbifarten"),
                session.thoughts());
        // Hela poängen med revideringen: en tanke som strukits ska vara borta.
        assertTrue(session.thoughts().stream().noneMatch(t -> t.contains("sover dåligt")));
    }

    @Test
    void tomRevideringBehallerDeGamlaTankarna() {
        ConversationSession session = sessionMedTankar();

        session.replaceThoughts("   \n\n");

        assertEquals(List.of("sover dåligt", "verkar stressad"), session.thoughts());
    }

    @Test
    void nullRevideringBehallerDeGamlaTankarna() {
        ConversationSession session = sessionMedTankar();

        session.replaceThoughts(null);

        assertEquals(2, session.thoughts().size());
    }

    @Test
    void gamlaFormatetLaggerFortfarandeTillTankar() {
        ConversationSession session = sessionMedTankar();

        session.addThoughtLines("- ny observation");

        assertEquals(3, session.thoughts().size());
        assertEquals("ny observation", session.thoughts().getLast());
    }
}
