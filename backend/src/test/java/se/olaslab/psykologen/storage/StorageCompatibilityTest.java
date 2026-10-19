package se.olaslab.psykologen.storage;

import static spola.test.Assertions.assertEquals;
import static spola.test.Assertions.assertTrue;
import static spola.test.Tests.suite;
import static spola.test.Tests.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import se.olaslab.psykologen.prompt.PromptStore;
import spola.test.Suite;

/** Filerna som Spring-versionen skrev med Jackson läses som förut, så en uppgradering tappar inget. */
public final class StorageCompatibilityTest {

    private StorageCompatibilityTest() {
    }

    public static Suite tests() {
        return suite("StorageCompatibilityTest",
                test("historikFranJacksonLasesOchFylls", StorageCompatibilityTest::historikFranJacksonLasesOchFylls),
                test("trasigHistorikGerTomLista", StorageCompatibilityTest::trasigHistorikGerTomLista),
                test("promptinstallningarFranJacksonLases", StorageCompatibilityTest::promptinstallningarFranJacksonLases),
                test("aldrePromptfilUtanNyaFaltGerStandardvarden", StorageCompatibilityTest::aldrePromptfilUtanNyaFaltGerStandardvarden),
                test("promptinstallningarSparasOchLasesTillbaka", StorageCompatibilityTest::promptinstallningarSparasOchLasesTillbaka));
    }

    static void historikFranJacksonLasesOchFylls() throws Exception {
        Path dir = Files.createTempDirectory("psykologen-lagring");
        try {
            Files.writeString(dir.resolve("history.json"), """
                    [{"change":"- Ny: sover dåligt","elapsedMinutes":3.5,"timestamp":1760000000000,"type":"profile"},\
                    {"change":null,"elapsedMinutes":4.0,"timestamp":1760000001000,"type":"plan"}]""");
            var store = new FileSessionArtifactStore(dir);

            store.appendHistory(new HistoryEntry(HistoryEntry.THREADS, 1760000002000L, 5.25, "- Öppnad: brodern"));

            assertEquals(List.of(
                    new HistoryEntry("threads", 1760000002000L, 5.25, "- Öppnad: brodern"),
                    new HistoryEntry("plan", 1760000001000L, 4.0, null),
                    new HistoryEntry("profile", 1760000000000L, 3.5, "- Ny: sover dåligt")),
                    store.readHistory());
        } finally {
            delete(dir);
        }
    }

    static void trasigHistorikGerTomLista() throws Exception {
        Path dir = Files.createTempDirectory("psykologen-lagring");
        try {
            Files.writeString(dir.resolve("history.json"), "[{\"type\":");
            assertEquals(List.of(), new FileSessionArtifactStore(dir).readHistory());
        } finally {
            delete(dir);
        }
    }

    static void promptinstallningarFranJacksonLases() throws Exception {
        Path dir = Files.createTempDirectory("psykologen-lagring");
        try {
            // Jacksons pretty printer, med ett fält som en framtida version kan ha lagt till.
            Files.writeString(dir.resolve("prompts.json"), """
                    {
                      "useCustomPrompts" : true,
                      "customPrompts" : {
                        "openingInstruction" : "Säg hej."
                      },
                      "sessionDurationMinutes" : 30.0,
                      "contextStrategy" : "window",
                      "nyttFalt" : 1
                    }""");

            var store = new PromptStore(dir);

            assertTrue(store.isUseCustomPrompts());
            assertEquals("Säg hej.", store.getOpeningInstruction());
            assertEquals(30.0, store.getSessionDurationMinutes(), 1e-9);
            assertEquals("window", store.getContextStrategy());
        } finally {
            delete(dir);
        }
    }

    static void aldrePromptfilUtanNyaFaltGerStandardvarden() throws Exception {
        Path dir = Files.createTempDirectory("psykologen-lagring");
        try {
            Files.writeString(dir.resolve("prompts.json"), """
                    {"useCustomPrompts":false,"customPrompts":{}}""");

            var store = new PromptStore(dir);

            assertEquals(PromptStore.DEFAULT_SESSION_DURATION_MINUTES, store.getSessionDurationMinutes(), 1e-9);
            assertEquals("full", store.getContextStrategy());
        } finally {
            delete(dir);
        }
    }

    static void promptinstallningarSparasOchLasesTillbaka() throws Exception {
        Path dir = Files.createTempDirectory("psykologen-lagring");
        try {
            var store = new PromptStore(dir);
            store.setSessionDurationMinutes(20);
            store.setContextStrategy("profile");
            store.update(Map.of("openingInstruction", "Börja lugnt."));

            var reloaded = new PromptStore(dir);

            assertEquals(20.0, reloaded.getSessionDurationMinutes(), 1e-9);
            assertEquals("profile", reloaded.getContextStrategy());
            assertTrue(reloaded.isUseCustomPrompts());
            assertEquals("Börja lugnt.", reloaded.getOpeningInstruction());
        } finally {
            delete(dir);
        }
    }

    private static void delete(Path dir) throws IOException {
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
