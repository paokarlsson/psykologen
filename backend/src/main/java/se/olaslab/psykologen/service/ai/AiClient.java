package se.olaslab.psykologen.service.ai;

import java.util.List;

import se.olaslab.psykologen.session.ChatMessage;

public interface AiClient {

    String providerName();

    String requiredEnvVar();

    boolean isConfigured();

    AiResponse chat(List<ChatMessage> messages) throws Exception;
}
