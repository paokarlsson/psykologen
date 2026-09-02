package com.example.session;

import java.util.ArrayList;
import java.util.List;

import com.example.service.ai.AiResponse;

/**
 * Allt muterbart tillstånd för ett pågående terapisamtal: meddelande-
 * historik, terapeutens inre reflektioner, tokenräknare och tidtagning.
 * En ren tillståndsklass - ingen I/O, inga AI-anrop; det sköter
 * {@link com.example.service.PsykologenService} och dess samarbetare.
 */
public class ConversationSession {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final List<String> internalThoughts = new ArrayList<>();
    private final long sessionStartTime = System.currentTimeMillis();

    private int conversationCount = 0;
    private int totalInputTokens = 0;
    private int totalOutputTokens = 0;

    public ConversationSession(String systemPrompt) {
        messages.add(ChatMessage.instruction(Role.SYSTEM, systemPrompt));
    }

    /** Hela historiken, som en oföränderlig ögonblicksbild. */
    public List<ChatMessage> messages() {
        return List.copyOf(messages);
    }

    /**
     * Historiken utan det senast tillagda meddelandet - används för att
     * bygga ett tillfälligt AI-anrop där sista turen ersätts med ett eget
     * instruktionsmeddelande, istället för att skicka den råa användartexten.
     */
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

    /** Tolkar en rå, radbruten text med nya tankar (t.ex. "- observation") och lägger till dem. */
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
