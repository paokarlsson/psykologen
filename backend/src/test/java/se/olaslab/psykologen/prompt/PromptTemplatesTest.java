package se.olaslab.psykologen.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import se.olaslab.psykologen.intervention.Interventions;

class PromptTemplatesTest {

    @Test
    void patientprofilenFyllsIStandardmallen() {
        String renderad = PromptTemplates.erikResponse(
                PromptStore.defaults().get(PromptStore.ERIK_RESPONSE),
                "- tanke", "Patienten heter Kim.", "Plan", "", Interventions.DEFAULT, 5.0, 45.0, "hej");

        assertTrue(renderad.contains("Patienten heter Kim."));
        assertTrue(!renderad.contains("{{patientProfile}}"));
    }

    @Test
    void tomProfilGerFallbacktext() {
        String renderad = PromptTemplates.erikResponse("Profil: {{patientProfile}}",
                "", "", "", "", Interventions.DEFAULT, 5.0, 45.0, "hej");

        assertEquals("Profil: Ingen profil än.", renderad);
    }

    @Test
    void reflektionsmallenBerOmEnAndringsloggOchHelaListan() {
        String renderad = PromptTemplates.thoughtReflection(
                PromptStore.defaults().get(PromptStore.THOUGHT_REFLECTION), "jag sover fint", "- sover dåligt");

        // Utan rubrikerna faller PsykologenService tillbaka på det gamla, adderande beteendet.
        ChangelogResponse mall = ChangelogResponse.parse(renderad);
        assertTrue(mall.changelog() != null);
        assertTrue(renderad.contains("jag sover fint"));
        assertTrue(renderad.contains("- sover dåligt"));
    }

    @Test
    void oppnaTradarFyllsIStandardmallen() {
        String renderad = PromptTemplates.erikResponse(
                PromptStore.defaults().get(PromptStore.ERIK_RESPONSE),
                "", "", "", "- nämnde brodern, bytte ämne", Interventions.DEFAULT, 5.0, 45.0, "hej");

        assertTrue(renderad.contains("- nämnde brodern, bytte ämne"));
        assertTrue(!renderad.contains("{{openThreads}}"));
    }

    @Test
    void tradmallenPararErikssenasteReplikMedPatientensNya() {
        String renderad = PromptTemplates.threadsUpdate(
                PromptStore.defaults().get(PromptStore.THREADS_UPDATE),
                "- sömnen", "Hur känns det?", "helt okej, förresten min bror...");

        assertTrue(renderad.contains("- sömnen"));
        assertTrue(renderad.contains("Hur känns det?"));
        assertTrue(renderad.contains("helt okej, förresten min bror..."));
    }

    @Test
    void aterstaendeTidBlirAldrigNegativ() {
        String renderad = PromptTemplates.erikResponse("Kvar: {{remainingMinutes}}",
                "", "", "", "", Interventions.DEFAULT, 60.0, 45.0, "hej");

        // Decimaltecknet beror på locale, därför bara tecknet som spelar roll här.
        assertTrue(!renderad.contains("-"));
    }
}
