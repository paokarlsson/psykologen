package com.example.session;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Vem som "pratar" i ett {@link ChatMessage}. {@link #wireValue()} ger den
 * gemena strängform både AI-leverantörernas API:er och frontend förväntar
 * sig ("system"/"user"/"assistant"), och är också den form Jackson
 * serialiserar rollen som i JSON-svaren till frontend.
 */
public enum Role {
    SYSTEM,
    USER,
    ASSISTANT;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }
}
