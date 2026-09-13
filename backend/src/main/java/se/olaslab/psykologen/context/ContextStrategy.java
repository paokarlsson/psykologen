package se.olaslab.psykologen.context;

import java.util.List;

import se.olaslab.psykologen.session.ChatMessage;
import se.olaslab.psykologen.session.ConversationSession;
import se.olaslab.psykologen.storage.SessionArtifactStore;

/**
 * Bestämmer vad modellen får se av samtalet.
 *
 * <p>Appen skickade länge hela historiken varje gång, två anrop per tur. Det fungerar, men
 * kostnaden växer kvadratiskt med samtalslängden. Strategierna gör alternativen jämförbara:
 * Glaslådan visar kontextstorlek och kostnad per tur, du avgör själv om Erik håller ihop.
 */
public interface ContextStrategy {

    String id();

    String label();

    /** Kort förklaring till användaren i inställningarna. */
    String description();

    /** Meddelandena som ska föregå den renderade mallen i ett anrop. */
    List<ChatMessage> build(ConversationSession session, SessionArtifactStore store);
}
