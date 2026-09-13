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
    void aterstaendeTidBlirAldrigNegativ() {
        String renderad = PromptTemplates.erikResponse("Kvar: {{remainingMinutes}}",
                "", "", "", 60.0, 45.0, "hej");

        // Decimaltecknet beror på locale, därför bara tecknet som spelar roll här.
        assertTrue(!renderad.contains("-"));
    }
}
