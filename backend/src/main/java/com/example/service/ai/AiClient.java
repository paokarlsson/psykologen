package com.example.service.ai;

import java.util.List;

import com.example.session.ChatMessage;

public interface AiClient {

    String providerName();

    String requiredEnvVar();

    boolean isConfigured();

    AiResponse chat(List<ChatMessage> messages) throws Exception;
}
