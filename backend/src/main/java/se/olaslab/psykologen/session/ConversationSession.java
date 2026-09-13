package se.olaslab.psykologen.session;

import java.util.ArrayList;
import java.util.List;

import se.olaslab.psykologen.service.ai.AiResponse;
import se.olaslab.psykologen.trace.TraceRecorder;

public class ConversationSession {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final List<String> internalThoughts = new ArrayList<>();
    private final long sessionStartTime = System.currentTimeMillis();

    // Traceen hör till sessionen och försvinner med den - en ny session börjar tom.
    private final TraceRecorder tracer = new TraceRecorder();

    private int conversationCount = 0;
    private int totalInputTokens = 0;
    private int totalOutputTokens = 0;

    public ConversationSession(String systemPrompt) {
        messages.add(ChatMessage.instruction(Role.SYSTEM, systemPrompt));
    }

    public List<ChatMessage> messages() {
        return List.copyOf(messages);
    }

    public TraceRecorder tracer() {
        return tracer;
    }

    /** Antal användarturer hittills. 0 innan det första meddelandet, alltså under öppningen. */
    public int turn() {
        return conversationCount;
    }

    public List<String> thoughts() {
        return List.copyOf(internalThoughts);
    }

    public List<ChatMessage> historyBeforeLastMessage() {
        return List.copyOf(messages.subList(0, messages.size() - 1));
    }

    /** Eriks senaste replik, tom sträng innan han sagt något. */
    public String lastAgentMessage() {
        return messages.reversed().stream()
                .filter(message -> message.role() == Role.ASSISTANT)
                .map(ChatMessage::content)
                .findFirst()
                .orElse("");
    }

    public void addUserMessage(String content) {
        messages.add(stampedMessage(Role.USER, content));
    }

    public void addAssistantMessage(String content) {
        messages.add(stampedMessage(Role.ASSISTANT, content));
    }

    private ChatMessage stampedMessage(Role role, String content) {
        long now = System.currentTimeMillis();
        return new ChatMessage(role, content, now, now - sessionStartTime);
    }

    public String thoughtsAsBulletText() {
        return String.join("\n", internalThoughts.stream().map(t -> "- " + t).toList());
    }

    public void addThoughtLines(String rawThoughts) {
        if (rawThoughts == null || rawThoughts.startsWith("Inga")) {
            return;
        }
        internalThoughts.addAll(parseThoughtLines(rawThoughts));
    }

    /**
     * Ersätter hela tankelistan med den reviderade versionen.
     *
     * <p>Poängen med revideringen är att strykningar ska slå igenom - en lista som bara växer
     * blir brus i prompten. Ett tomt svar ignoreras ändå: hellre gamla tankar än inga alls.
     */
    public void replaceThoughts(String rawThoughts) {
        if (rawThoughts == null) {
            return;
        }
        List<String> reviderade = parseThoughtLines(rawThoughts);
        if (reviderade.isEmpty()) {
            return;
        }
        internalThoughts.clear();
        internalThoughts.addAll(reviderade);
    }

    private static List<String> parseThoughtLines(String rawThoughts) {
        List<String> thoughts = new ArrayList<>();
        for (String rawLine : rawThoughts.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("- ")) {
                thoughts.add(line.substring(2));
            } else if (!line.isEmpty() && !line.startsWith("-")) {
                thoughts.add(line);
            }
        }
        return thoughts;
    }

    public double elapsedMinutes() {
        return (System.currentTimeMillis() - sessionStartTime) / 60000.0;
    }

    public void recordUsage(AiResponse response) {
        totalInputTokens += response.inputTokens();
        totalOutputTokens += response.outputTokens();
    }

    public void incrementConversationCount() {
        conversationCount++;
    }
}
