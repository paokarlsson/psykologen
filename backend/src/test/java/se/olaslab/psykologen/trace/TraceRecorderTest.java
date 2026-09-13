package se.olaslab.psykologen.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import se.olaslab.psykologen.service.ai.AiResponse;
import se.olaslab.psykologen.session.ChatMessage;
import se.olaslab.psykologen.session.Role;

class TraceRecorderTest {

    private static final List<ChatMessage> SKICKAT =
            List.of(ChatMessage.instruction(Role.USER, "hej"));

    @Test
    void lyckatAnropSpelasInMedTokensOchKostnad() throws Exception {
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

    @Test
    void okandModellGerIngenKostnadIStalletForGissning() throws Exception {
        TraceRecorder recorder = new TraceRecorder();

        recorder.record(LlmCall.SVAR, 1, SKICKAT,
                messages -> new AiResponse("svar", "modell-utan-pris", 1000, 200, 0, 0));

        assertNull(recorder.calls().getFirst().costUsd());
        assertTrue(recorder.calls().getFirst().error() == null);
    }

    @Test
    void felSpelasInOchKastasVidare() {
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

    @Test
    void aldreAnropFallerUrNarTaketNas() throws Exception {
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

    @Test
    void sammanfattningenSummerarOchPekarUtAktuellKontext() throws Exception {
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

    @Test
    void sammanfattningenFlaggarNarPrisSaknas() throws Exception {
        TraceRecorder recorder = new TraceRecorder();

        recorder.record(LlmCall.SVAR, 1, SKICKAT,
                messages -> new AiResponse("svar", "modell-utan-pris", 100, 10, 0, 0));

        assertTrue(!recorder.summary().costComplete());
    }
}
