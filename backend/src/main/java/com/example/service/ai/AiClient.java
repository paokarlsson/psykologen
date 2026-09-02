package com.example.service.ai;

import java.util.List;

import com.example.session.ChatMessage;

/**
 * En chat-completion-leverantör. {@link com.example.service.PsykologenService}
 * pratar bara med detta interface, aldrig direkt med OpenAI eller
 * Anthropic, så den aktiva leverantören kan bytas (se
 * {@link com.example.PsykologenApplication}) utan att röra affärslogiken.
 */
public interface AiClient {

    /** Läsbart leverantörsnamn, används i felmeddelanden (t.ex. "Anthropic"). */
    String providerName();

    /** Namnet på miljövariabeln som håller denna leverantörs API-nyckel. */
    String requiredEnvVar();

    /** Om API-nyckeln finns, dvs. om denna klient kan användas. */
    boolean isConfigured();

    /** Skickar hittillsvarande konversation och returnerar assistentens svar. */
    AiResponse chat(List<ChatMessage> messages) throws Exception;
}
