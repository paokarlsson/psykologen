package se.olaslab.psykologen.trace;

/**
 * @param costComplete          false när något anrop kördes på en modell utan pris i
 *                              {@link Prislista} - summan är då en underskattning
 * @param currentContextTokens  inputTokens i senaste svarsanropet, alltså hur stor kontext
 *                              Erik faktiskt fick. Siffran som gör minnesstrategierna jämförbara.
 */
public record TraceSummary(
        int callCount,
        int totalInputTokens,
        int totalOutputTokens,
        double totalCostUsd,
        boolean costComplete,
        long avgLatencyMs,
        int currentContextTokens,
        int errorCount) {
}
