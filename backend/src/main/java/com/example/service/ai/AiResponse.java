package com.example.service.ai;

/**
 * Provider-agnostic result of a chat call: the assistant's text plus
 * token usage, normalized from whichever provider ({@link OpenAiClient}
 * or {@link AnthropicChatClient}) produced it.
 */
public record AiResponse(String text, int inputTokens, int outputTokens) {
}
