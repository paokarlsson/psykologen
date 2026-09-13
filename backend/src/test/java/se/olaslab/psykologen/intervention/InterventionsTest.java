package se.olaslab.psykologen.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InterventionsTest {

    @Test
    void rentIdMatchar() {
        assertEquals("spegling", Interventions.match("spegling").id());
    }

    @Test
    void idIEnMeningMatchar() {
        // En liten modell packar gärna in valet i en mening trots instruktionen.
        assertEquals("skalfraga", Interventions.match("Jag väljer skalfraga här.").id());
    }

    @Test
    void versalerOchRadbrytningarSpelarIngenRoll() {
        assertEquals("undantag", Interventions.match("\nUNDANTAG\n").id());
    }

    @Test
    void okantSvarFallerTillbakaPaOppenFraga() {
        assertSame(Interventions.DEFAULT, Interventions.match("motiverande samtal"));
        assertSame(Interventions.DEFAULT, Interventions.match(""));
        assertSame(Interventions.DEFAULT, Interventions.match(null));
    }

    @Test
    void katalogenListasMedIdOchNarGreppetPassar() {
        String lista = Interventions.asPromptList();

        for (Intervention intervention : Interventions.all()) {
            assertTrue(lista.contains(intervention.id()), "saknar " + intervention.id());
            assertTrue(lista.contains(intervention.whenToUse()), "saknar beskrivning av " + intervention.id());
        }
    }
}
