package se.olaslab.psykologen.context;

import java.util.ArrayList;
import java.util.List;

import se.olaslab.psykologen.session.ChatMessage;
import se.olaslab.psykologen.session.ConversationSession;
import se.olaslab.psykologen.session.Role;
import se.olaslab.psykologen.storage.SessionArtifactStore;

/**
 * Rå historik byts mot destillatet: patientprofilen som bakgrundsjobbet redan skriver, plus de
 * allra senaste replikerna.
 *
 * <p>Det här är experimentets skarpa ände. Profilen är i praktiken en löpande sammanfattning av
 * samtalet - räcker den för att Erik ska hålla ihop som person, eller behövs de faktiska orden?
 */
public class ProfileStrategy implements ContextStrategy {

    public static final String ID = "profile";

    /** Två utbyten fram och tillbaka, så att den omedelbara tråden inte tappas. */
    private static final int FONSTER = 4;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Profil + senaste replikerna";
    }

    @Override
    public String description() {
        return "Historiken ersätts av patientprofilen och de " + FONSTER + " senaste replikerna. "
                + "Minsta kontexten - testar om profilen duger som minne.";
    }

    @Override
    public List<ChatMessage> build(ConversationSession session, SessionArtifactStore store) {
        List<ChatMessage> history = session.historyBeforeLastMessage();

        List<ChatMessage> result = new ArrayList<>(ContextMessages.systemMessages(history));
        store.readProfile()
                .filter(profile -> !profile.isBlank())
                .ifPresent(profile -> result.add(ChatMessage.instruction(Role.SYSTEM,
                        "Vad du hittills vet om patienten:\n\n" + profile)));
        result.addAll(ContextMessages.last(ContextMessages.dialogue(history), FONSTER));
        return List.copyOf(result);
    }
}
