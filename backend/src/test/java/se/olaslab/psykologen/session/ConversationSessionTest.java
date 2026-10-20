package se.olaslab.psykologen.session;

import static spola.test.Assertions.assertEquals;
import static spola.test.Assertions.assertTrue;
import static spola.test.Tests.suite;
import static spola.test.Tests.test;

import java.util.List;

import spola.test.Suite;

public final class ConversationSessionTest {

    private ConversationSessionTest() {
    }

    public static Suite tests() {
        return suite("ConversationSessionTest",
                test("revideringErsatterHelaTankelistan", ConversationSessionTest::revideringErsatterHelaTankelistan),
                test("tomRevideringBehallerDeGamlaTankarna", ConversationSessionTest::tomRevideringBehallerDeGamlaTankarna),
                test("nullRevideringBehallerDeGamlaTankarna", ConversationSessionTest::nullRevideringBehallerDeGamlaTankarna),
                test("senasteErikRepliknArAldreAnDenNyaAnvandarrepliken", ConversationSessionTest::senasteErikRepliknArAldreAnDenNyaAnvandarrepliken),
                test("senasteErikRepliknArTomFoereOppningen", ConversationSessionTest::senasteErikRepliknArTomFoereOppningen),
                test("gamlaFormatetLaggerFortfarandeTillTankar", ConversationSessionTest::gamlaFormatetLaggerFortfarandeTillTankar));
    }

    private static ConversationSession sessionMedTankar() {
        ConversationSession session = new ConversationSession("Du är Erik.");
        session.addThoughtLines("- sover dåligt\n- verkar stressad");
        return session;
    }

    static void revideringErsatterHelaTankelistan() {
        ConversationSession session = sessionMedTankar();

        session.replaceThoughts("- verkar stressad, troligen jobbet\n- nämnde brodern i förbifarten");

        assertEquals(List.of("verkar stressad, troligen jobbet", "nämnde brodern i förbifarten"),
                session.thoughts());
        // Hela poängen med revideringen: en tanke som strukits ska vara borta.
        assertTrue(session.thoughts().stream().noneMatch(t -> t.contains("sover dåligt")));
    }

    static void tomRevideringBehallerDeGamlaTankarna() {
        ConversationSession session = sessionMedTankar();

        session.replaceThoughts("   \n\n");

        assertEquals(List.of("sover dåligt", "verkar stressad"), session.thoughts());
    }

    static void nullRevideringBehallerDeGamlaTankarna() {
        ConversationSession session = sessionMedTankar();

        session.replaceThoughts(null);

        assertEquals(2, session.thoughts().size());
    }

    static void senasteErikRepliknArAldreAnDenNyaAnvandarrepliken() {
        ConversationSession session = new ConversationSession("Du är Erik.");
        session.addAssistantMessage("Hej, hur mår du?");
        session.addUserMessage("sådär faktiskt");

        // Profilanropet körs före Eriks svar och paras med hans föregående replik.
        assertEquals("Hej, hur mår du?", session.lastAgentMessage());
    }

    static void senasteErikRepliknArTomFoereOppningen() {
        assertEquals("", new ConversationSession("Du är Erik.").lastAgentMessage());
    }

    static void gamlaFormatetLaggerFortfarandeTillTankar() {
        ConversationSession session = sessionMedTankar();

        session.addThoughtLines("- ny observation");

        assertEquals(3, session.thoughts().size());
        assertEquals("ny observation", session.thoughts().getLast());
    }
}
