package se.olaslab.psykologen.service.ai;

import static spola.json.codec.JsonCodec.field;
import static spola.json.codec.JsonCodec.optionalField;
import static spola.json.codec.JsonCodecs.INT;
import static spola.json.codec.JsonCodecs.STRING;
import static spola.json.codec.JsonCodecs.list;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import se.olaslab.psykologen.session.ChatMessage;
import se.olaslab.psykologen.session.Role;
import se.olaslab.psykologen.spolabehov.retry.Retry;
import spola.client.RestClient;
import spola.json.codec.JsonCodec;
import spola.util.Lazy;

/** Anthropics Messages API över {@code spola.client}. */
public class AnthropicChatClient implements AiClient {

    public static final URI API = URI.create("https://api.anthropic.com");

    private static final String MODEL = "claude-haiku-4-5"; // billig modell för test
    private static final int MAX_TOKENS = 16000;
    // Som SDK:ns standard: ett svar på 16 000 tokens kan ta flera minuter.
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final String apiKey;
    private final Retry retry;
    private final Lazy<RestClient> client;

    public AnthropicChatClient(String apiKey) {
        this(apiKey, API, Retry.defaults());
    }

    public AnthropicChatClient(String apiKey, URI baseUri, Retry retry) {
        this.apiKey = apiKey;
        this.retry = retry;
        // Byggs först vid första anropet: utan nyckel ska appen kunna säga det, inte fallera här.
        this.client = Lazy.of(() -> RestClient.builder()
                .baseUri(baseUri)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .timeout(TIMEOUT)
                .build());
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

    @Override
    public AiResponse chat(List<ChatMessage> messages) throws Exception {
        StringBuilder systemPrompt = new StringBuilder();
        List<Message> conversation = new ArrayList<>();

        for (ChatMessage msg : messages) {
            if (msg.role() == Role.SYSTEM) {
                if (systemPrompt.length() > 0) {
                    systemPrompt.append("\n\n");
                }
                systemPrompt.append(msg.content());
            } else {
                Role role = msg.role() == Role.ASSISTANT ? Role.ASSISTANT : Role.USER;
                conversation.add(new Message(role, msg.content()));
            }
        }

        // Anthropic kräver att första turen är "user", men appens öppningsreplik
        // kommer från assistenten.
        if (!conversation.isEmpty() && conversation.get(0).role() == Role.ASSISTANT) {
            conversation.add(0, new Message(Role.USER, "Starta samtalet."));
        }

        var request = new MessagesRequest(MODEL, MAX_TOKENS,
                systemPrompt.isEmpty() ? Optional.empty() : Optional.of(systemPrompt.toString()),
                conversation);
        MessagesResponse response = retry.call(() -> client.get().post("/v1/messages")
                .json(request, MessagesRequest.CODEC)
                .retrieve(MessagesResponse.CODEC));

        StringBuilder text = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(text::append);
        }

        // Cache-fälten är noll så länge inga cacheControl-brytpunkter sätts. De redovisas
        // ändå: att de är noll är hela poängen med att visa dem i Glaslådan.
        Usage usage = response.usage();
        return new AiResponse(text.toString(), MODEL, usage.inputTokens(), usage.outputTokens(),
                usage.cacheReadInputTokens().orElse(0), usage.cacheCreationInputTokens().orElse(0));
    }

    record Message(Role role, String content) {

        static final JsonCodec<Message> CODEC = JsonCodec.object(Message::new,
                field("role", Role.CODEC, Message::role),
                field("content", STRING, Message::content));
    }

    record MessagesRequest(String model, int maxTokens, Optional<String> system, List<Message> messages) {

        static final JsonCodec<MessagesRequest> CODEC = JsonCodec.object(MessagesRequest::new,
                field("model", STRING, MessagesRequest::model),
                field("max_tokens", INT, MessagesRequest::maxTokens),
                optionalField("system", STRING, MessagesRequest::system),
                field("messages", list(Message.CODEC), MessagesRequest::messages));
    }

    // API:t lägger till medlemmar och blocktyper med tiden: läs bara det appen använder.

    record ContentBlock(String type, Optional<String> text) {

        static final JsonCodec<ContentBlock> CODEC = JsonCodec.object(ContentBlock::new,
                field("type", STRING, ContentBlock::type),
                optionalField("text", STRING, ContentBlock::text)).ignoringUnknown();
    }

    record Usage(int inputTokens, int outputTokens, Optional<Integer> cacheReadInputTokens,
            Optional<Integer> cacheCreationInputTokens) {

        static final JsonCodec<Usage> CODEC = JsonCodec.object(Usage::new,
                field("input_tokens", INT, Usage::inputTokens),
                field("output_tokens", INT, Usage::outputTokens),
                optionalField("cache_read_input_tokens", INT, Usage::cacheReadInputTokens),
                optionalField("cache_creation_input_tokens", INT, Usage::cacheCreationInputTokens))
                .ignoringUnknown();
    }

    record MessagesResponse(List<ContentBlock> content, Usage usage) {

        static final JsonCodec<MessagesResponse> CODEC = JsonCodec.object(MessagesResponse::new,
                field("content", list(ContentBlock.CODEC), MessagesResponse::content),
                field("usage", Usage.CODEC, MessagesResponse::usage)).ignoringUnknown();
    }
}
