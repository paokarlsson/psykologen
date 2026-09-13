package se.olaslab.psykologen.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PromptTemplatesTest {

    @Test
    void patientprofilenFyllsIStandardmallen() {
        String renderad = PromptTemplates.erikResponse(
                PromptStore.defaults().get(PromptStore.ERIK_RESPONSE),
                "- tanke", "Patienten heter Kim.", "Plan", 5.0, 45.0, "hej");

        assertTrue(renderad.contains("Patienten heter Kim."));
        assertTrue(!renderad.contains("{{patientProfile}}"));
    }

    @Test
    void tomProfilGerFallbacktext() {
        String renderad = PromptTemplates.erikResponse("Profil: {{patientProfile}}",
                "", "", "", 5.0, 45.0, "hej");

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
    void aterstaendeTidBlirAldrigNegativ() {
        String renderad = PromptTemplates.erikResponse("Kvar: {{remainingMinutes}}",
                "", "", "", 60.0, 45.0, "hej");

        // Decimaltecknet beror på locale, därför bara tecknet som spelar roll här.
        assertTrue(!renderad.contains("-"));
    }
}
