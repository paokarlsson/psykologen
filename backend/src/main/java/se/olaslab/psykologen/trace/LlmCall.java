package se.olaslab.psykologen.trace;

import static spola.json.codec.JsonCodec.field;
import static spola.json.codec.JsonCodecs.DOUBLE;
import static spola.json.codec.JsonCodecs.INT;
import static spola.json.codec.JsonCodecs.LONG;
import static spola.json.codec.JsonCodecs.STRING;
import static spola.json.codec.JsonCodecs.list;
import static spola.json.codec.JsonCodecs.nullable;

import java.util.List;

import se.olaslab.psykologen.session.ChatMessage;
import spola.json.codec.JsonCodec;

/**
 * Ett enskilt LLM-anrop, så som det faktiskt gick iväg.
 *
 * <p>{@code sentMessages} är hela nyttan: systemprompten, historiken och det syntetiska
 * user-meddelandet med den renderade mallen - exakt det modellen såg.
 *
 * @param costUsd null när modellen saknar pris i {@link Prislista}
 * @param error   null när anropet lyckades
 */
public record LlmCall(
        String steg,
        int turn,
        long startedAt,
        long latencyMs,
        String model,
        List<ChatMessage> sentMessages,
        String responseText,
        int inputTokens,
        int outputTokens,
        int cacheReadTokens,
        int cacheCreationTokens,
        Double costUsd,
        String error) {

    public static final String OPPNING = "oppning";
    public static final String REFLEKTION = "reflektion";
    public static final String METOD = "metod";
    public static final String SVAR = "svar";
    public static final String PROFIL = "profil";
    public static final String TRADAR = "tradar";
    public static final String PLAN = "plan";

    /** {@code model}, {@code responseText}, {@code costUsd} och {@code error} skrivs som {@code null} när de saknas. */
    public static final JsonCodec<LlmCall> CODEC = JsonCodec.object(LlmCall::new,
            field("steg", STRING, LlmCall::steg),
            field("turn", INT, LlmCall::turn),
            field("startedAt", LONG, LlmCall::startedAt),
            field("latencyMs", LONG, LlmCall::latencyMs),
            field("model", nullable(STRING), LlmCall::model),
            field("sentMessages", list(ChatMessage.CODEC), LlmCall::sentMessages),
            field("responseText", nullable(STRING), LlmCall::responseText),
            field("inputTokens", INT, LlmCall::inputTokens),
            field("outputTokens", INT, LlmCall::outputTokens),
            field("cacheReadTokens", INT, LlmCall::cacheReadTokens),
            field("cacheCreationTokens", INT, LlmCall::cacheCreationTokens),
            field("costUsd", nullable(DOUBLE), LlmCall::costUsd),
            field("error", nullable(STRING), LlmCall::error));
}
