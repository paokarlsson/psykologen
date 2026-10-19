package se.olaslab.psykologen.trace;

import static spola.json.codec.JsonCodec.field;
import static spola.json.codec.JsonCodecs.BOOLEAN;
import static spola.json.codec.JsonCodecs.DOUBLE;
import static spola.json.codec.JsonCodecs.INT;
import static spola.json.codec.JsonCodecs.LONG;

import spola.json.codec.JsonCodec;

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

    public static final JsonCodec<TraceSummary> CODEC = JsonCodec.object(TraceSummary::new,
            field("callCount", INT, TraceSummary::callCount),
            field("totalInputTokens", INT, TraceSummary::totalInputTokens),
            field("totalOutputTokens", INT, TraceSummary::totalOutputTokens),
            field("totalCostUsd", DOUBLE, TraceSummary::totalCostUsd),
            field("costComplete", BOOLEAN, TraceSummary::costComplete),
            field("avgLatencyMs", LONG, TraceSummary::avgLatencyMs),
            field("currentContextTokens", INT, TraceSummary::currentContextTokens),
            field("errorCount", INT, TraceSummary::errorCount));
}
