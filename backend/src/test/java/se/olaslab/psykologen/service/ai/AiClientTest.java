package se.olaslab.psykologen.service.ai;

import static java.nio.charset.StandardCharsets.UTF_8;
import static spola.test.Assertions.assertEquals;
import static spola.test.Assertions.assertFalse;
import static spola.test.Assertions.assertThrows;
import static spola.test.Assertions.assertTrue;
import static spola.test.Tests.suite;
import static spola.test.Tests.test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import se.olaslab.psykologen.session.ChatMessage;
import se.olaslab.psykologen.session.Role;
import se.olaslab.psykologen.spolabehov.retry.Retry;
import spola.client.RestClientResponseException;
import spola.client.RestClientTransportException;
import spola.http.Headers;
import spola.http.HttpServer;
import spola.http.HttpServerOptions;
import spola.http.Response;
import spola.json.Json;
import spola.json.JsonArray;
import spola.json.JsonObject;
import spola.test.Suite;

/** AI-klienterna mot servrar som spelar Anthropic och OpenAI. */
public final class AiClientTest {

    private static final List<ChatMessage> SAMTAL = List.of(
            ChatMessage.instruction(Role.SYSTEM, "Du är Erik."),
            ChatMessage.instruction(Role.SYSTEM, "Profil: sover dåligt."),
            new ChatMessage(Role.ASSISTANT, "Hej, hur mår du?", 1L, 0L),
            new ChatMessage(Role.USER, "Sådär.", 2L, 1L));

    private static final String ANTHROPIC_SVAR = """
            {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-4-5",
             "content":[{"type":"thinking","thinking":"..."},{"type":"text","text":"Hej "},{"type":"text","text":"igen"}],
             "stop_reason":"end_turn",
             "usage":{"input_tokens":120,"output_tokens":7,"cache_read_input_tokens":null,
                      "cache_creation_input_tokens":3,"service_tier":"standard"}}
            """;

    private AiClientTest() {
    }

    public static Suite tests() {
        return suite("AiClientTest",
                test("anthropicFarHuvudenaOchKroppenSomApietVill", AiClientTest::anthropicFarHuvudenaOchKroppenSomApietVill),
                test("anthropicForsokerIgenVid529OchFoljerRetryAfter", AiClientTest::anthropicForsokerIgenVid529OchFoljerRetryAfter),
                test("anthropicGerUppEfterTvaOmforsok", AiClientTest::anthropicGerUppEfterTvaOmforsok),
                test("anthropicForsokerInteIgenVid400OchVisarInteNyckeln", AiClientTest::anthropicForsokerInteIgenVid400OchVisarInteNyckeln),
                test("anthropicForsokerIgenNarAnslutningenNekas", AiClientTest::anthropicForsokerIgenNarAnslutningenNekas),
                test("openAiFarBearerOchSvaretLases", AiClientTest::openAiFarBearerOchSvaretLases));
    }

    static void anthropicFarHuvudenaOchKroppenSomApietVill() throws Exception {
        try (var api = new FakeApi(json(200, ANTHROPIC_SVAR))) {
            var client = new AnthropicChatClient("sk-ant-test", api.uri(), new Retry(2, delay -> { }));

            AiResponse response = client.chat(SAMTAL);

            assertEquals(new AiResponse("Hej igen", "claude-haiku-4-5", 120, 7, 0, 3), response);
            Seen seen = api.seen.getFirst();
            assertEquals("POST", seen.method());
            assertEquals("/v1/messages", seen.path());
            assertEquals("sk-ant-test", seen.headers().first("x-api-key").orElseThrow());
            assertEquals("2023-06-01", seen.headers().first("anthropic-version").orElseThrow());
            assertTrue(seen.headers().first("content-type").orElseThrow().startsWith("application/json"));

            JsonObject body = seen.body();
            assertEquals("claude-haiku-4-5", body.string("model"));
            assertEquals(16000, body.number("max_tokens").intValueExact());
            assertEquals("Du är Erik.\n\nProfil: sover dåligt.", body.string("system"));
            JsonArray messages = body.array("messages");
            assertEquals(3, messages.size());
            // Anthropic kräver att första turen är user.
            assertEquals("user", messages.object(0).string("role"));
            assertEquals("Starta samtalet.", messages.object(0).string("content"));
            assertEquals("assistant", messages.object(1).string("role"));
            assertEquals("user", messages.object(2).string("role"));
            assertEquals("Sådär.", messages.object(2).string("content"));
        }
    }

    static void anthropicForsokerIgenVid529OchFoljerRetryAfter() throws Exception {
        try (var api = new FakeApi(
                json(529, "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\"}}").withHeader("retry-after", "2"),
                json(200, ANTHROPIC_SVAR))) {
            List<Duration> waits = new ArrayList<>();
            var client = new AnthropicChatClient("sk-ant-test", api.uri(), new Retry(2, waits::add));

            assertEquals("Hej igen", client.chat(SAMTAL).text());
            assertEquals(2, api.seen.size());
            assertEquals(List.of(Duration.ofSeconds(2)), waits);
        }
    }

    static void anthropicGerUppEfterTvaOmforsok() throws Exception {
        try (var api = new FakeApi(json(500, "{}"), json(502, "{}"), json(503, "{}"))) {
            List<Duration> waits = new ArrayList<>();
            var client = new AnthropicChatClient("sk-ant-test", api.uri(), new Retry(2, waits::add));

            var e = assertThrows(RestClientResponseException.class, () -> client.chat(SAMTAL));

            assertEquals(503, e.status());
            assertEquals(3, api.seen.size());
            assertEquals(2, waits.size());
        }
    }

    static void anthropicForsokerInteIgenVid400OchVisarInteNyckeln() throws Exception {
        try (var api = new FakeApi(json(401, "{\"error\":{\"message\":\"invalid x-api-key: sk-ant-test\"}}"))) {
            var client = new AnthropicChatClient("sk-ant-test", api.uri(), new Retry(2, delay -> { }));

            var e = assertThrows(RestClientResponseException.class, () -> client.chat(SAMTAL));

            assertEquals(401, e.status());
            assertEquals(1, api.seen.size());
            // Felet visas i traceen och i svaret till frontenden - nyckeln får inte följa med.
            assertFalse(e.getMessage().contains("sk-ant-test"), e.getMessage());
        }
    }

    static void anthropicForsokerIgenNarAnslutningenNekas() throws Exception {
        URI stangd;
        try (var api = new FakeApi()) {
            stangd = api.uri();
        }
        List<Duration> waits = new ArrayList<>();
        var client = new AnthropicChatClient("sk-ant-test", stangd, new Retry(2, waits::add));

        assertThrows(RestClientTransportException.class, () -> client.chat(SAMTAL));
        assertEquals(2, waits.size());
    }

    static void openAiFarBearerOchSvaretLases() throws Exception {
        try (var api = new FakeApi(json(200, """
                {"id":"chatcmpl-1","object":"chat.completion","choices":[{"index":0,
                 "message":{"role":"assistant","content":"Hej","refusal":null},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}}
                """))) {
            var client = new OpenAiClient("sk-test", api.uri());

            assertEquals(new AiResponse("Hej", "gpt-4o-mini", 10, 2, 0, 0), client.chat(SAMTAL));

            Seen seen = api.seen.getFirst();
            assertEquals("/v1/chat/completions", seen.path());
            assertEquals("Bearer sk-test", seen.headers().first("authorization").orElseThrow());
            assertEquals("gpt-4o-mini", seen.body().string("model"));
            JsonArray messages = seen.body().array("messages");
            assertEquals(4, messages.size());
            assertEquals("system", messages.object(0).string("role"));
            assertEquals("assistant", messages.object(2).string("role"));
            assertEquals("Sådär.", messages.object(3).string("content"));
        }
    }

    private static Response json(int status, String body) {
        return Response.bytes(status, "application/json", body.getBytes(UTF_8));
    }

    private record Seen(String method, String path, Headers headers, JsonObject body) {
    }

    /** Svarar med svaren i tur och ordning och sparar förfrågningarna. */
    private static final class FakeApi implements AutoCloseable {

        final List<Seen> seen = new CopyOnWriteArrayList<>();
        private final HttpServer server;

        FakeApi(Response... replies) throws IOException {
            var queue = new ConcurrentLinkedQueue<>(List.of(replies));
            server = HttpServer.start(HttpServerOptions.DEFAULT.withPort(0), request -> {
                seen.add(new Seen(request.method(), request.path(), request.headers(),
                        (JsonObject) Json.parse(request.bytes())));
                return queue.remove();
            });
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + server.port());
        }

        @Override
        public void close() {
            server.close();
        }
    }
}
