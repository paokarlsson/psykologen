package se.olaslab.psykologen.trace;

import static spola.test.Assertions.assertEquals;
import static spola.test.Assertions.assertNotNull;
import static spola.test.Assertions.assertNull;
import static spola.test.Assertions.assertThrows;
import static spola.test.Assertions.assertTrue;
import static spola.test.Tests.suite;
import static spola.test.Tests.test;

import java.util.List;

import se.olaslab.psykologen.service.ai.AiResponse;
import se.olaslab.psykologen.session.ChatMessage;
import se.olaslab.psykologen.session.Role;
import spola.test.Suite;

public final class TraceRecorderTest {

    private TraceRecorderTest() {
    }

    public static Suite tests() {
        return suite("TraceRecorderTest",
                test("lyckatAnropSpelasInMedTokensOchKostnad", TraceRecorderTest::lyckatAnropSpelasInMedTokensOchKostnad),
                test("okandModellGerIngenKostnadIStalletForGissning", TraceRecorderTest::okandModellGerIngenKostnadIStalletForGissning),
                test("felSpelasInOchKastasVidare", TraceRecorderTest::felSpelasInOchKastasVidare),
                test("aldreAnropFallerUrNarTaketNas", TraceRecorderTest::aldreAnropFallerUrNarTaketNas),
                test("sammanfattningenSummerarOchPekarUtAktuellKontext", TraceRecorderTest::sammanfattningenSummerarOchPekarUtAktuellKontext),
                test("sammanfattningenFlaggarNarPrisSaknas", TraceRecorderTest::sammanfattningenFlaggarNarPrisSaknas));
    }

    private static final List<ChatMessage> SKICKAT =
            List.of(ChatMessage.instruction(Role.USER, "hej"));

    static void lyckatAnropSpelasInMedTokensOchKostnad() throws Exception {
        TraceRecorder recorder = new TraceRecorder();

        recorder.record(LlmCall.SVAR, 3, SKICKAT,
                messages -> new AiResponse("svar", "claude-haiku-4-5", 1000, 200, 0, 0));

        LlmCall call = recorder.calls().getFirst();
        assertEquals(LlmCall.SVAR, call.steg());
        assertEquals(3, call.turn());
        assertEquals("svar", call.responseText());
        assertEquals(1000, call.inputTokens());
        assertNull(call.error());
        // 1000 in á $1/miljon + 200 ut á $5/miljon = 0.002
        assertEquals(0.002, call.costUsd(), 1e-9);
    }

    static void okandModellGerIngenKostnadIStalletForGissning() throws Exception {
        TraceRecorder recorder = new TraceRecorder();

        recorder.record(LlmCall.SVAR, 1, SKICKAT,
                messages -> new AiResponse("svar", "modell-utan-pris", 1000, 200, 0, 0));

        assertNull(recorder.calls().getFirst().costUsd());
        assertTrue(recorder.calls().getFirst().error() == null);
    }

    static void felSpelasInOchKastasVidare() {
        TraceRecorder recorder = new TraceRecorder();

        assertThrows(IllegalStateException.class, () -> recorder.record(LlmCall.PROFIL, 2, SKICKAT,
                messages -> {
                    throw new IllegalStateException("api nere");
                }));

        LlmCall call = recorder.calls().getFirst();
        assertEquals(LlmCall.PROFIL, call.steg());
        assertNotNull(call.error());
        assertTrue(call.error().contains("api nere"));
        // Det som skickades finns kvar även när anropet gick fel - det är då man vill se det.
        assertEquals(SKICKAT, call.sentMessages());
    }

    static void aldreAnropFallerUrNarTaketNas() throws Exception {
        TraceRecorder recorder = new TraceRecorder();

        for (int i = 0; i < TraceRecorder.MAX_ANROP + 10; i++) {
            int turn = i;
            recorder.record(LlmCall.SVAR, turn, SKICKAT,
                    messages -> new AiResponse("svar", "claude-haiku-4-5", 1, 1, 0, 0));
        }

        List<LlmCall> calls = recorder.calls();
        assertEquals(TraceRecorder.MAX_ANROP, calls.size());
        // Senaste först, och de tio äldsta är borta.
        assertEquals(TraceRecorder.MAX_ANROP + 9, calls.getFirst().turn());
        assertEquals(10, calls.getLast().turn());
    }

    static void sammanfattningenSummerarOchPekarUtAktuellKontext() throws Exception {
        TraceRecorder recorder = new TraceRecorder();

        recorder.record(LlmCall.REFLEKTION, 1, SKICKAT,
                messages -> new AiResponse("tankar", "claude-haiku-4-5", 500, 50, 0, 0));
        recorder.record(LlmCall.SVAR, 1, SKICKAT,
                messages -> new AiResponse("svar", "claude-haiku-4-5", 900, 30, 0, 0));

        TraceSummary summary = recorder.summary();
        assertEquals(2, summary.callCount());
        assertEquals(1400, summary.totalInputTokens());
        assertEquals(80, summary.totalOutputTokens());
        assertTrue(summary.costComplete());
        assertEquals(0, summary.errorCount());
        // Kontexten är vad Erik läste i sitt svar, inte summan av alla anrop.
        assertEquals(900, summary.currentContextTokens());
    }

    static void sammanfattningenFlaggarNarPrisSaknas() throws Exception {
        TraceRecorder recorder = new TraceRecorder();

        recorder.record(LlmCall.SVAR, 1, SKICKAT,
                messages -> new AiResponse("svar", "modell-utan-pris", 100, 10, 0, 0));

        assertTrue(!recorder.summary().costComplete());
    }
}
