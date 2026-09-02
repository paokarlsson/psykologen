package com.example.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ett meddelande i konversationshistoriken. {@code timestamp}/
 * {@code sessionTimeMs} är {@code null} för systemmeddelandet och för
 * instruktionsmeddelanden som bara skickas till AI-leverantören - precis
 * som tidigare saknades då nycklarna helt i den serialiserade JSON:en
 * (se {@link JsonInclude}), vilket frontend redan behandlar som valfritt.
 *
 * {@code session_time} behåller sitt gamla (snake_case) namn i JSON via
 * {@link JsonProperty} eftersom frontend redan pratar det formatet.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
        Role role,
        String content,
        Long timestamp,
        @JsonProperty("session_time") Long sessionTimeMs) {

    /** Ett meddelande som bara skickas till AI-leverantören, aldrig sparas i historiken. */
    public static ChatMessage instruction(Role role, String content) {
        return new ChatMessage(role, content, null, null);
    }
}
