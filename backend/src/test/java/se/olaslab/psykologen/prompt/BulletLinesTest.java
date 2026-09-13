package se.olaslab.psykologen.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class BulletLinesTest {

    @Test
    void bindestreckOchBlankraderStadasBort() {
        List<String> rader = BulletLines.parse("- första\n\n  - andra  \ntredje\n");

        assertEquals(List.of("första", "andra", "tredje"), rader);
    }

    @Test
    void tomtOchNullGerTomLista() {
        assertEquals(List.of(), BulletLines.parse(null));
        assertEquals(List.of(), BulletLines.parse("\n\n"));
    }

    @Test
    void ingaTraderKannsIgenSomTomtSvar() {
        assertTrue(BulletLines.isEmptyAnswer("Inga öppna trådar."));
        assertTrue(BulletLines.isEmptyAnswer("  Inga förändringar."));
        assertTrue(BulletLines.isEmptyAnswer(null));
        assertFalse(BulletLines.isEmptyAnswer("- nämnde brodern, bytte ämne"));
    }
}
