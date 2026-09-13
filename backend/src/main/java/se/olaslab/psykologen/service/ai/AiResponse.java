package se.olaslab.psykologen.service.ai;

public record AiResponse(String text, String model, int inputTokens, int outputTokens,
        int cacheReadTokens, int cacheCreationTokens) {
}
