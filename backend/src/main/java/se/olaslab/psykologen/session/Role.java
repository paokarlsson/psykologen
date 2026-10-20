package se.olaslab.psykologen.session;

import static spola.json.codec.JsonCodecs.STRING;

import spola.json.codec.JsonCodec;

public enum Role {
    SYSTEM,
    USER,
    ASSISTANT;

    /** {@code "system"}, {@code "user"} och {@code "assistant"}, som frontenden och AI-leverantörerna skriver rollen. */
    public static final JsonCodec<Role> CODEC = STRING.map(Role::fromWireValue, Role::wireValue);

    public String wireValue() {
        return name().toLowerCase();
    }

    public static Role fromWireValue(String value) {
        for (Role role : values()) {
            if (role.wireValue().equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Okänd roll: " + value);
    }
}
