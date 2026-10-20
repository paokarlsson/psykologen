package se.olaslab.psykologen.session;

import static spola.json.codec.JsonCodec.field;
import static spola.json.codec.JsonCodec.optionalField;
import static spola.json.codec.JsonCodecs.LONG;
import static spola.json.codec.JsonCodecs.STRING;

import java.util.Optional;

import spola.json.codec.JsonCodec;

public record ChatMessage(
        Role role,
        String content,
        Long timestamp,
        Long sessionTimeMs) {

    /** {@code timestamp} och {@code session_time} utelämnas när de saknas, som frontenden väntar sig. */
    public static final JsonCodec<ChatMessage> CODEC = JsonCodec.object(
            (role, content, timestamp, sessionTime) ->
                    new ChatMessage(role, content, timestamp.orElse(null), sessionTime.orElse(null)),
            field("role", Role.CODEC, ChatMessage::role),
            field("content", STRING, ChatMessage::content),
            optionalField("timestamp", LONG, (ChatMessage m) -> Optional.ofNullable(m.timestamp())),
            optionalField("session_time", LONG, (ChatMessage m) -> Optional.ofNullable(m.sessionTimeMs())));

    /** Meddelande som bara skickas till AI-leverantören, aldrig sparas i historiken. */
    public static ChatMessage instruction(Role role, String content) {
        return new ChatMessage(role, content, null, null);
    }
}
