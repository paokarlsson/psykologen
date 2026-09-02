package com.example.service.ai;

import java.util.List;
import java.util.Map;

/**
 * A chat-completion provider. {@link PsykologenService} talks only to
 * this interface, never to OpenAI or Anthropic directly, so the active
 * provider can be swapped via the {@code ai.provider} property
 * (see {@link AiClientConfig}) without touching business logic.
 */
public interface AiClient {

    /** Human-readable provider name, used in error messages (e.g. "Anthropic"). */
    String providerName();

    /** Name of the environment variable holding this provider's API key. */
    String requiredEnvVar();

    /** Whether the API key is present, i.e. whether this client can be used. */
    boolean isConfigured();

    /**
     * Sends the conversation so far and returns the assistant's reply.
     * Messages use the same {@code role}/{@code content} shape the rest
     * of the app already uses ("system", "user", "assistant"); each
     * implementation adapts that shape to its provider's wire format.
     */
    AiResponse chat(List<Map<String, Object>> messages) throws Exception;
}
