package com.example.service.ai;

import java.util.ArrayList;
import java.util.List;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlock;
import com.example.session.ChatMessage;
import com.example.session.Role;

/**
 * {@link AiClient} backad av Anthropics Messages API. Till skillnad från
 * OpenAI har Anthropic ingen "system"-roll i {@code messages} - system-
 * prompten är ett separat toppnivåfält, och första turen måste vara
 * "user". Den här adaptern delar ut ev. {@code role: "system"}-poster i
 * toppnivå-systemprompten, och - eftersom appens öppningsreplik sägs av
 * assistenten utan föregående user-tur i historiken - lägger till en
 * minimal syntetisk user-tur vid behov så konversationen fortfarande
 * börjar med "user".
 *
 * En vanlig, ramverksfri klass: API-nyckeln kommer in via konstruktorn.
 * Wiring sker i {@link com.example.PsykologenApplication}.
 */
public class AnthropicChatClient implements AiClient {

    private static final String MODEL = "claude-haiku-4-5"; // billig modell för test
    private static final long MAX_TOKENS = 16000L;

    private final String apiKey;
    private volatile AnthropicClient client;

    public AnthropicChatClient(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String providerName() {
        return "Anthropic";
    }

    @Override
    public String requiredEnvVar() {
        return "ANTHROPIC_API_KEY";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
                }
            }
        }
        return client;
    }

    @Override
    public AiResponse chat(List<ChatMessage> messages) throws Exception {
        StringBuilder systemPrompt = new StringBuilder();
        List<MessageParam> conversation = new ArrayList<>();

        for (ChatMessage msg : messages) {
            if (msg.role() == Role.SYSTEM) {
                if (systemPrompt.length() > 0) {
                    systemPrompt.append("\n\n");
                }
                systemPrompt.append(msg.content());
            } else {
                MessageParam.Role paramRole = msg.role() == Role.ASSISTANT
                        ? MessageParam.Role.ASSISTANT
                        : MessageParam.Role.USER;
                conversation.add(MessageParam.builder().role(paramRole).content(msg.content()).build());
            }
        }

        if (!conversation.isEmpty() && conversation.get(0).role() == MessageParam.Role.ASSISTANT) {
            conversation.add(0, MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content("Starta samtalet.")
                    .build());
        }

        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS);

        if (systemPrompt.length() > 0) {
            builder.system(systemPrompt.toString());
        }

        for (MessageParam m : conversation) {
            builder.addMessage(m);
        }

        Message response = client().messages().create(builder.build());

        StringBuilder text = new StringBuilder();
        for (TextBlock block : response.content().stream()
                .flatMap(b -> b.text().stream())
                .toList()) {
            text.append(block.text());
        }

        int inputTokens = (int) response.usage().inputTokens();
        int outputTokens = (int) response.usage().outputTokens();

        return new AiResponse(text.toString(), inputTokens, outputTokens);
    }
}
