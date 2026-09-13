package se.olaslab.psykologen.context;

import java.util.List;

import se.olaslab.psykologen.session.ChatMessage;
import se.olaslab.psykologen.session.Role;

/** Delade hjälpmetoder för strategierna. */
final class ContextMessages {

    private ContextMessages() {
    }

    /** Systemmeddelanden behålls alltid - de bär Eriks persona. */
    static List<ChatMessage> systemMessages(List<ChatMessage> history) {
        return history.stream().filter(m -> m.role() == Role.SYSTEM).toList();
    }

    static List<ChatMessage> dialogue(List<ChatMessage> history) {
        return history.stream().filter(m -> m.role() != Role.SYSTEM).toList();
    }

    static List<ChatMessage> last(List<ChatMessage> messages, int count) {
        int from = Math.max(0, messages.size() - count);
        return messages.subList(from, messages.size());
    }
}
