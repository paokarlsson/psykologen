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
        for (String rawLine : rawThoughts.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("- ")) {
                internalThoughts.add(line.substring(2));
            } else if (!line.isEmpty() && !line.startsWith("-")) {
                internalThoughts.add(line);
            }
        }
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
