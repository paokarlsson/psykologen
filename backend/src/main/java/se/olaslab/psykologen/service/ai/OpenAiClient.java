package se.olaslab.psykologen.service.ai;

import static spola.json.codec.JsonCodec.field;
import static spola.json.codec.JsonCodec.optionalField;
import static spola.json.codec.JsonCodecs.INT;
import static spola.json.codec.JsonCodecs.STRING;
import static spola.json.codec.JsonCodecs.list;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import se.olaslab.psykologen.session.ChatMessage;
import se.olaslab.psykologen.session.Role;
import spola.client.RestClient;
import spola.json.codec.JsonCodec;
import spola.util.Lazy;

/** OpenAIs Chat Completions över {@code spola.client}. */
public class OpenAiClient implements AiClient {

    public static final URI API = URI.create("https://api.openai.com");

    private static final String MODEL = "gpt-4o-mini";

    private final String apiKey;
    private final Lazy<RestClient> client;

    public OpenAiClient(String apiKey) {
        this(apiKey, API);
    }

    public OpenAiClient(String apiKey, URI baseUri) {
        this.apiKey = apiKey;
        this.client = Lazy.of(() -> RestClient.builder()
                .baseUri(baseUri)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build());
    }

    @Override
    public String providerName() {
        return "OpenAI";
    }

    @Override
    public String requiredEnvVar() {
        return "OPENAI_API_KEY";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public AiResponse chat(List<ChatMessage> messages) throws Exception {
        var request = new ChatRequest(MODEL, messages.stream()
                .map(msg -> new Message(msg.role(), msg.content()))
                .toList());

        ChatResponse response = client.get().post("/v1/chat/completions")
                .json(request, ChatRequest.CODEC)
                .retrieve(ChatResponse.CODEC);

        String text = response.choices().get(0).message().content();
        int inputTokens = response.usage().map(Usage::promptTokens).orElse(0);
        int outputTokens = response.usage().map(Usage::completionTokens).orElse(0);

        // OpenAI-svaret rapporterar ingen cache-uppdelning som mappas mot Anthropics fält.
        return new AiResponse(text, MODEL, inputTokens, outputTokens, 0, 0);
    }

    record Message(Role role, String content) {

        static final JsonCodec<Message> CODEC = JsonCodec.object(Message::new,
                field("role", Role.CODEC, Message::role),
                field("content", STRING, Message::content));
    }

    record ChatRequest(String model, List<Message> messages) {

        static final JsonCodec<ChatRequest> CODEC = JsonCodec.object(ChatRequest::new,
                field("model", STRING, ChatRequest::model),
                field("messages", list(Message.CODEC), ChatRequest::messages));
    }

    // API:t lägger till medlemmar med tiden: läs bara det appen använder.

    record ResponseMessage(String content) {

        static final JsonCodec<ResponseMessage> CODEC = JsonCodec.object(ResponseMessage::new,
                field("content", STRING, ResponseMessage::content)).ignoringUnknown();
    }

    record Choice(ResponseMessage message) {

        static final JsonCodec<Choice> CODEC = JsonCodec.object(Choice::new,
                field("message", ResponseMessage.CODEC, Choice::message)).ignoringUnknown();
    }

    record Usage(int promptTokens, int completionTokens) {

        static final JsonCodec<Usage> CODEC = JsonCodec.object(Usage::new,
                field("prompt_tokens", INT, Usage::promptTokens),
                field("completion_tokens", INT, Usage::completionTokens)).ignoringUnknown();
    }

    record ChatResponse(List<Choice> choices, Optional<Usage> usage) {

        static final JsonCodec<ChatResponse> CODEC = JsonCodec.object(ChatResponse::new,
                field("choices", list(Choice.CODEC), ChatResponse::choices),
                optionalField("usage", Usage.CODEC, ChatResponse::usage)).ignoringUnknown();
    }
}
