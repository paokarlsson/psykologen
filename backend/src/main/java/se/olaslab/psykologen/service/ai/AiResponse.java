package se.olaslab.psykologen.service.ai;

public record AiResponse(String text, int inputTokens, int outputTokens) {
}
