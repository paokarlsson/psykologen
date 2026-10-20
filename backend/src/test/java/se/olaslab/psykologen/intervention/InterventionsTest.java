package se.olaslab.psykologen.intervention;

import static spola.test.Assertions.assertEquals;
import static spola.test.Assertions.assertSame;
import static spola.test.Assertions.assertTrue;
import static spola.test.Tests.suite;
import static spola.test.Tests.test;

import spola.test.Suite;

public final class InterventionsTest {

    private InterventionsTest() {
    }

    public static Suite tests() {
        return suite("InterventionsTest",
                test("rentIdMatchar", InterventionsTest::rentIdMatchar),
                test("idIEnMeningMatchar", InterventionsTest::idIEnMeningMatchar),
                test("versalerOchRadbrytningarSpelarIngenRoll", InterventionsTest::versalerOchRadbrytningarSpelarIngenRoll),
                test("okantSvarFallerTillbakaPaOppenFraga", InterventionsTest::okantSvarFallerTillbakaPaOppenFraga),
                test("katalogenListasMedIdOchNarGreppetPassar", InterventionsTest::katalogenListasMedIdOchNarGreppetPassar));
    }

    static void rentIdMatchar() {
        assertEquals("spegling", Interventions.match("spegling").id());
    }

    static void idIEnMeningMatchar() {
        // En liten modell packar gärna in valet i en mening trots instruktionen.
        assertEquals("skalfraga", Interventions.match("Jag väljer skalfraga här.").id());
    }

    static void versalerOchRadbrytningarSpelarIngenRoll() {
        assertEquals("undantag", Interventions.match("\nUNDANTAG\n").id());
    }

    static void okantSvarFallerTillbakaPaOppenFraga() {
        assertSame(Interventions.DEFAULT, Interventions.match("motiverande samtal"));
        assertSame(Interventions.DEFAULT, Interventions.match(""));
        assertSame(Interventions.DEFAULT, Interventions.match(null));
    }

    static void katalogenListasMedIdOchNarGreppetPassar() {
        String lista = Interventions.asPromptList();

        for (Intervention intervention : Interventions.all()) {
            assertTrue(lista.contains(intervention.id()), "saknar " + intervention.id());
            assertTrue(lista.contains(intervention.whenToUse()), "saknar beskrivning av " + intervention.id());
        }
    }
}
