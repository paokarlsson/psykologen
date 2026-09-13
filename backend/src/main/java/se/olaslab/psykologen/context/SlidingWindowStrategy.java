package se.olaslab.psykologen.context;

import java.util.ArrayList;
import java.util.List;

import se.olaslab.psykologen.session.ChatMessage;
import se.olaslab.psykologen.session.ConversationSession;
import se.olaslab.psykologen.storage.SessionArtifactStore;

/**
 * Systemprompten plus de senaste replikerna. Kontexten slutar växa, men allt äldre är borta -
 * Erik tappar det som sades i början av samtalet.
 */
public class SlidingWindowStrategy implements ContextStrategy {

    public static final String ID = "window";

    /** Antal repliker som behålls. Sex räcker till ungefär tre utbyten fram och tillbaka. */
    private static final int FONSTER = 6;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Glidande fönster (" + FONSTER + " repliker)";
    }

    @Override
    public String description() {
        return "Bara de " + FONSTER + " senaste replikerna följer med. Kontexten slutar växa, "
                + "men Erik minns inte samtalets början.";
    }

    @Override
    public List<ChatMessage> build(ConversationSession session, SessionArtifactStore store) {
        List<ChatMessage> history = session.historyBeforeLastMessage();

        List<ChatMessage> result = new ArrayList<>(ContextMessages.systemMessages(history));
        List<ChatMessage> dialogue = ContextMessages.dialogue(history);
        result.addAll(ContextMessages.last(dialogue, FONSTER));
        return List.copyOf(result);
    }
}
