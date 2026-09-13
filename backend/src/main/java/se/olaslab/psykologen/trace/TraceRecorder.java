package se.olaslab.psykologen.trace;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import se.olaslab.psykologen.service.ai.AiResponse;
import se.olaslab.psykologen.session.ChatMessage;

/**
 * Spelar in varje LLM-anrop i en session: vad som skickades, vad som kom tillbaka, hur lång
 * tid det tog, vad det kostade och om det gick fel.
 *
 * <p>Lever bara i minnet och bara så länge sessionen gör det. Innehållet är hela samtalet i
 * klartext och får aldrig skrivas till disk.
 *
 * <p>Bakgrundsjobben skriver från egna trådar, därav synkroniseringen.
 */
public class TraceRecorder {

    /** Tak för hur många anrop som sparas. Äldre anrop faller ur - minnet ska inte växa fritt. */
    public static final int MAX_ANROP = 200;

    private final Deque<LlmCall> calls = new ArrayDeque<>();

    /** Själva anropet, så att inspelningen kan lägga sig runt det. */
    @FunctionalInterface
    public interface AiCall {
        AiResponse invoke(List<ChatMessage> messages) throws Exception;
    }

    /**
     * Kör anropet och spelar in det. Undantag spelas in med {@code error} satt och kastas
     * vidare - anroparen avgör som förut om felet ska störa samtalet.
     */
    public AiResponse record(String steg, int turn, List<ChatMessage> sent, AiCall call) throws Exception {
        List<ChatMessage> skickat = List.copyOf(sent);
        long start = System.currentTimeMillis();
        try {
            AiResponse response = call.invoke(sent);
            add(new LlmCall(steg, turn, start, System.currentTimeMillis() - start, response.model(),
                    skickat, response.text(), response.inputTokens(), response.outputTokens(),
                    response.cacheReadTokens(), response.cacheCreationTokens(),
                    Prislista.kostnad(response.model(), response.inputTokens(), response.outputTokens())
                            .orElse(null),
                    null));
            return response;
        } catch (Exception e) {
            add(new LlmCall(steg, turn, start, System.currentTimeMillis() - start, null,
                    skickat, null, 0, 0, 0, 0, null, beskriv(e)));
            throw e;
        }
    }

    /** Senaste anropet först - det är det man vill läsa. */
    public List<LlmCall> calls() {
        synchronized (calls) {
            return calls.stream().toList().reversed();
        }
    }

    public TraceSummary summary() {
        List<LlmCall> snapshot;
        synchronized (calls) {
            snapshot = List.copyOf(calls);
        }

        int inputTokens = 0;
        int outputTokens = 0;
        long latencySum = 0;
        int errorCount = 0;
        double cost = 0;
        boolean costComplete = true;
        int contextTokens = 0;

        for (LlmCall call : snapshot) {
            inputTokens += call.inputTokens();
            outputTokens += call.outputTokens();
            latencySum += call.latencyMs();
            if (call.error() != null) {
                errorCount++;
                continue;
            }
            if (call.costUsd() == null) {
                costComplete = false;
            } else {
                cost += call.costUsd();
            }
            if (LlmCall.SVAR.equals(call.steg())) {
                contextTokens = call.inputTokens();
            }
        }

        long avgLatency = snapshot.isEmpty() ? 0 : latencySum / snapshot.size();
        return new TraceSummary(snapshot.size(), inputTokens, outputTokens, cost, costComplete,
                avgLatency, contextTokens, errorCount);
    }

    private void add(LlmCall call) {
        synchronized (calls) {
            calls.addLast(call);
            while (calls.size() > MAX_ANROP) {
                calls.removeFirst();
            }
        }
    }

    private static String beskriv(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": " + message;
    }
}
