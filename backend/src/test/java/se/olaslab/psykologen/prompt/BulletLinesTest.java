package se.olaslab.psykologen.prompt;

import static spola.test.Assertions.assertEquals;
import static spola.test.Assertions.assertFalse;
import static spola.test.Assertions.assertTrue;
import static spola.test.Tests.suite;
import static spola.test.Tests.test;

import java.util.List;

import spola.test.Suite;

public final class BulletLinesTest {

    private BulletLinesTest() {
    }

    public static Suite tests() {
        return suite("BulletLinesTest",
                test("bindestreckOchBlankraderStadasBort", BulletLinesTest::bindestreckOchBlankraderStadasBort),
                test("tomtOchNullGerTomLista", BulletLinesTest::tomtOchNullGerTomLista),
                test("ingaTraderKannsIgenSomTomtSvar", BulletLinesTest::ingaTraderKannsIgenSomTomtSvar));
    }

    static void bindestreckOchBlankraderStadasBort() {
        List<String> rader = BulletLines.parse("- första\n\n  - andra  \ntredje\n");

        assertEquals(List.of("första", "andra", "tredje"), rader);
    }

    static void tomtOchNullGerTomLista() {
        assertEquals(List.of(), BulletLines.parse(null));
        assertEquals(List.of(), BulletLines.parse("\n\n"));
    }

    static void ingaTraderKannsIgenSomTomtSvar() {
        assertTrue(BulletLines.isEmptyAnswer("Inga öppna trådar."));
        assertTrue(BulletLines.isEmptyAnswer("  Inga förändringar."));
        assertTrue(BulletLines.isEmptyAnswer(null));
        assertFalse(BulletLines.isEmptyAnswer("- nämnde brodern, bytte ämne"));
    }
}
