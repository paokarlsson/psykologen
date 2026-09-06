package se.olaslab.psykologen.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
        Role role,
        String content,
        Long timestamp,
        @JsonProperty("session_time") Long sessionTimeMs) {

    /** Meddelande som bara skickas till AI-leverantören, aldrig sparas i historiken. */
    public static ChatMessage instruction(Role role, String content) {
        return new ChatMessage(role, content, null, null);
    }
}
