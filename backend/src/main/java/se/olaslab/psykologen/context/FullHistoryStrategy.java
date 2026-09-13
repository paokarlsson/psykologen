package se.olaslab.psykologen.context;

import java.util.List;

import se.olaslab.psykologen.session.ChatMessage;
import se.olaslab.psykologen.session.ConversationSession;
import se.olaslab.psykologen.storage.SessionArtifactStore;

/** Hela samtalet, varje gång. Appens ursprungliga beteende och fortfarande standard. */
public class FullHistoryStrategy implements ContextStrategy {

    public static final String ID = "full";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Hela historiken";
    }

    @Override
    public String description() {
        return "Allt som sagts skickas med i varje anrop. Erik glömmer inget, "
                + "men kontexten och kostnaden växer för varje replik.";
    }

    @Override
    public List<ChatMessage> build(ConversationSession session, SessionArtifactStore store) {
        return session.historyBeforeLastMessage();
    }
}
