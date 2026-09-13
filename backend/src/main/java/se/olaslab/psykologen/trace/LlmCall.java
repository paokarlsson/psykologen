package se.olaslab.psykologen.trace;

import java.util.List;

import se.olaslab.psykologen.session.ChatMessage;

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
    public static final String SVAR = "svar";
    public static final String PROFIL = "profil";
    public static final String PLAN = "plan";
}
