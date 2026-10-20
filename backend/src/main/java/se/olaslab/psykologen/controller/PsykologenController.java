package se.olaslab.psykologen.controller;

import static spola.json.codec.JsonCodec.field;
import static spola.json.codec.JsonCodecs.BOOLEAN;
import static spola.json.codec.JsonCodecs.DOUBLE;
import static spola.json.codec.JsonCodecs.STRING;
import static spola.json.codec.JsonCodecs.list;
import static spola.json.codec.JsonCodecs.map;
import static spola.json.codec.JsonCodecs.nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import se.olaslab.psykologen.prompt.PromptSettings;
import se.olaslab.psykologen.service.PsykologenService;
import se.olaslab.psykologen.service.UserSessionRegistry;
import se.olaslab.psykologen.session.ChatMessage;
import se.olaslab.psykologen.storage.HistoryEntry;
import se.olaslab.psykologen.trace.LlmCall;
import se.olaslab.psykologen.trace.TraceSummary;
import spola.http.Response;
import spola.json.JsonNull;
import spola.json.JsonNumber;
import spola.json.JsonObject;
import spola.json.JsonString;
import spola.json.JsonValue;
import spola.json.codec.JsonCodec;
import spola.security.web.WebSecurity;
import spola.web.Responses;
import spola.web.RouterBuilder;
import spola.web.WebRequest;

/** {@code /api/psykologen}: samtalet, artefakterna, traceen och inställningarna för den inloggade användaren. */
public class PsykologenController {

    private static final String BASE = "/api/psykologen";

    private static final JsonCodec<List<ChatMessage>> CONVERSATION = list(ChatMessage.CODEC);
    private static final JsonCodec<List<HistoryEntry>> HISTORY = list(HistoryEntry.CODEC);
    private static final JsonCodec<List<LlmCall>> CALLS = list(LlmCall.CODEC);
    private static final JsonCodec<List<String>> LINES = list(STRING);

    // Kropparna läses som i Spring-versionen: okända fält ignoreras och ett saknat fält är null.

    record MessageBody(String message) {
        static final JsonCodec<MessageBody> CODEC = JsonCodec.object(MessageBody::new,
                field("message", nullable(STRING), MessageBody::message).orElse(null)).ignoringUnknown();
    }

    record PromptKey(String key) {
        static final JsonCodec<PromptKey> CODEC = JsonCodec.object(PromptKey::new,
                field("key", nullable(STRING), PromptKey::key).orElse(null)).ignoringUnknown();
    }

    record Enabled(Boolean enabled) {
        static final JsonCodec<Enabled> CODEC = JsonCodec.object(Enabled::new,
                field("enabled", nullable(BOOLEAN), Enabled::enabled).orElse(null)).ignoringUnknown();
    }

    record Strategy(String strategy) {
        static final JsonCodec<Strategy> CODEC = JsonCodec.object(Strategy::new,
                field("strategy", nullable(STRING), Strategy::strategy).orElse(null)).ignoringUnknown();
    }

    record Minutes(Double minutes) {
        static final JsonCodec<Minutes> CODEC = JsonCodec.object(Minutes::new,
                field("minutes", nullable(DOUBLE), Minutes::minutes).orElse(null)).ignoringUnknown();
    }

    private static final JsonCodec<Map<String, String>> PROMPT_UPDATES = map(STRING);

    private final UserSessionRegistry registry;

    public PsykologenController(UserSessionRegistry registry) {
        this.registry = registry;
    }

    public RouterBuilder routes(RouterBuilder routes) {
        return routes
                .post(BASE + "/start", this::startConversation)
                .post(BASE + "/message", this::sendMessage)
                .get(BASE + "/conversation", this::getConversation)
                .get(BASE + "/profile", this::getProfile)
                .get(BASE + "/plan", this::getPlan)
                .get(BASE + "/history", this::getHistory)
                .get(BASE + "/trace", this::getTrace)
                .post(BASE + "/reset", this::resetSession)
                .get(BASE + "/settings/prompts", this::getPromptSettings)
                .put(BASE + "/settings/prompts", this::updatePrompts)
                .post(BASE + "/settings/prompts/reset", this::resetPrompts)
                .put(BASE + "/settings/custom-prompts-enabled", this::setCustomPromptsEnabled)
                .put(BASE + "/settings/context-strategy", this::setContextStrategy)
                .put(BASE + "/settings/session-duration", this::setSessionDuration);
    }

    private PsykologenService serviceFor(WebRequest request) {
        return registry.forUser(WebSecurity.requireIdentity(request).name());
    }

    /**
     * Sessionens mätare följer med varje svar som rör samtalet: tiden, eftersom
     * Erik anpassar sig efter hur mycket som är kvar, och vad anropen kostat
     * hittills. Kostnaden ändras bara när ett anrop görs, så den hör hemma här
     * i stället för i en egen pollning - /trace bär hela prompthistoriken och
     * är alldeles för tung att hämta för ett belopp.
     */
    private static JsonObject.Builder addTiming(JsonObject.Builder response, PsykologenService service) {
        TraceSummary trace = service.getTraceSummary();
        return response
                .put("elapsedMinutes", JsonNumber.of(service.elapsedMinutes()))
                .put("sessionDurationMinutes", JsonNumber.of(service.getSessionDurationMinutes()))
                .put("costUsd", JsonNumber.of(trace.totalCostUsd()))
                .put("costComplete", trace.costComplete());
    }

    Response startConversation(WebRequest request) {
        try {
            PsykologenService service = serviceFor(request);
            String opening = service.startConversation();
            return ok(request, addTiming(success()
                    .put("message", opening)
                    .put("role", "erik"), service));
        } catch (Exception e) {
            return failure(request, e);
        }
    }

    Response sendMessage(WebRequest request) throws IOException {
        String userInput = request.readJson(MessageBody.CODEC).message();
        try {
            if (userInput == null || userInput.trim().isEmpty()) {
                return failure(request, "Message cannot be empty");
            }

            PsykologenService service = serviceFor(request);
            String erikResponse = service.processMessage(userInput);
            JsonObject.Builder response = addTiming(success()
                    .put("message", erikResponse)
                    .put("role", "erik"), service);

            if (erikResponse.contains("KLAR FÖR SKRIVNING")) {
                response.put("sessionComplete", true);
            }

            return ok(request, response);
        } catch (Exception e) {
            return failure(request, e);
        }
    }

    Response getConversation(WebRequest request) {
        try {
            PsykologenService service = serviceFor(request);
            return ok(request, addTiming(success()
                    .put("conversation", CONVERSATION.encode(service.getConversation())), service));
        } catch (Exception e) {
            return failure(request, e);
        }
    }

    Response getProfile(WebRequest request) {
        try {
            return ok(request, success().put("profile", serviceFor(request).getProfile()));
        } catch (Exception e) {
            return failure(request, e);
        }
    }

    Response getPlan(WebRequest request) {
        try {
            return ok(request, success().put("plan", serviceFor(request).getPlan()));
        } catch (Exception e) {
            return failure(request, e);
        }
    }

    Response getHistory(WebRequest request) {
        try {
            return ok(request, success().put("history", HISTORY.encode(serviceFor(request).getHistory())));
        } catch (Exception e) {
            return failure(request, e);
        }
    }

    Response getTrace(WebRequest request) {
        try {
            PsykologenService service = serviceFor(request);
            return ok(request, success()
                    .put("calls", CALLS.encode(service.getTrace()))
                    .put("thoughts", LINES.encode(service.getThoughts()))
                    .put("openThreads", LINES.encode(service.getOpenThreads()))
                    .put("summary", TraceSummary.CODEC.encode(service.getTraceSummary())));
        } catch (Exception e) {
            return failure(request, e);
        }
    }

    Response resetSession(WebRequest request) {
        try {
            serviceFor(request).resetSession();
            return ok(request, success());
        } catch (Exception e) {
            return failure(request, e);
        }
    }

    Response getPromptSettings(WebRequest request) {
        try {
            JsonObject settings = (JsonObject) PromptSettings.CODEC.encode(serviceFor(request).getPromptSettings());
            JsonObject.Builder response = JsonObject.builder();
            settings.members().forEach(response::put);
            return ok(request, response.put("success", true));
        } catch (Exception e) {
            return failure(request, e);
        }
    }

    Response updatePrompts(WebRequest request) throws IOException {
        Map<String, String> updates = request.readJson(PROMPT_UPDATES);
        try {
            serviceFor(request).updatePrompts(updates);
            return ok(request, success());
        } catch (IllegalArgumentException e) {
            return failure(request, e);
        }
    }

    Response resetPrompts(WebRequest request) throws IOException {
        // Kroppen är valfri: utan den återställs alla promptar.
        String key = request.contentType().isPresent() ? request.readJson(PromptKey.CODEC).key() : null;
        if (key == null || key.isBlank()) {
            serviceFor(request).resetAllPrompts();
        } else {
            serviceFor(request).resetPrompt(key);
        }
        return ok(request, success());
    }

    Response setCustomPromptsEnabled(WebRequest request) throws IOException {
        boolean enabled = Boolean.TRUE.equals(request.readJson(Enabled.CODEC).enabled());
        serviceFor(request).setCustomPromptsEnabled(enabled);
        return ok(request, success().put("enabled", enabled));
    }

    Response setContextStrategy(WebRequest request) throws IOException {
        String id = request.readJson(Strategy.CODEC).strategy();
        try {
            if (id == null || id.isBlank()) {
                return failure(request, "Fältet 'strategy' saknas.");
            }
            serviceFor(request).setContextStrategy(id);
            return ok(request, success().put("contextStrategy", id));
        } catch (IllegalArgumentException e) {
            return failure(request, e);
        }
    }

    Response setSessionDuration(WebRequest request) throws IOException {
        Double minutes = request.readJson(Minutes.CODEC).minutes();
        try {
            if (minutes == null) {
                return failure(request, "Fältet 'minutes' saknas.");
            }
            serviceFor(request).setSessionDurationMinutes(minutes);
            return ok(request, success().put("sessionDurationMinutes", JsonNumber.of(minutes)));
        } catch (IllegalArgumentException e) {
            return failure(request, e);
        }
    }

    private static JsonObject.Builder success() {
        return JsonObject.builder().put("success", true);
    }

    private static Response ok(WebRequest request, JsonObject.Builder response) {
        return Responses.json(request, 200, response.build());
    }

    private static Response failure(WebRequest request, Exception e) {
        return failure(request, e.getMessage());
    }

    private static Response failure(WebRequest request, String error) {
        JsonValue message = error == null ? JsonNull.INSTANCE : JsonString.of(error);
        return Responses.json(request, 400, JsonObject.builder()
                .put("success", false)
                .put("error", message)
                .build());
    }
}
