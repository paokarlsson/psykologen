package se.olaslab.psykologen.session;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
    SYSTEM,
    USER,
    ASSISTANT;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }
}
