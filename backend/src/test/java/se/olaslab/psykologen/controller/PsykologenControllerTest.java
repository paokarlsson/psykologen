package se.olaslab.psykologen.controller;

import static spola.test.Assertions.assertEquals;
import static spola.test.Assertions.assertFalse;
import static spola.test.Assertions.assertTrue;
import static spola.test.Tests.suite;
import static spola.test.Tests.test;

import java.io.IOException;

import se.olaslab.psykologen.Browser;
import se.olaslab.psykologen.TestApp;
import spola.json.JsonObject;
import spola.json.JsonValue;
import spola.test.Suite;

/** Svaren har de fält och typer som frontendens {@code api.service.ts} läser. */
public final class PsykologenControllerTest {

    private static TestApp app;

    private PsykologenControllerTest() {
    }

    public static Suite tests() {
        return suite("PsykologenControllerTest",
                test("startOchMeddelandeBarMatarna", PsykologenControllerTest::startOchMeddelandeBarMatarna),
                test("samtaletHarRollerMedSmaBokstaverOchSessionTime", PsykologenControllerTest::samtaletHarRollerMedSmaBokstaverOchSessionTime),
                test("tomtMeddelandeGer400MedFelText", PsykologenControllerTest::tomtMeddelandeGer400MedFelText),
                test("trasigJsonGer400", PsykologenControllerTest::trasigJsonGer400),
                test("traceHarAnropOchSammanfattning", PsykologenControllerTest::traceHarAnropOchSammanfattning),
                test("installningarnaHarPromptarOchStrategier", PsykologenControllerTest::installningarnaHarPromptarOchStrategier),
                test("installningarSparasOchFelRapporteras", PsykologenControllerTest::installningarSparasOchFelRapporteras),
                test("historikenArEnLista", PsykologenControllerTest::historikenArEnLista))
                .beforeAll(() -> app = TestApp.start())
                .afterAll(() -> app.close());
    }

    private static Browser inloggad() throws IOException, InterruptedException {
        Browser browser = app.browser();
        assertEquals(200, browser.login("anna", TestApp.ANNA_PASSWORD).status());
        return browser;
    }

    static void startOchMeddelandeBarMatarna() throws Exception {
        Browser browser = inloggad();

        JsonObject start = browser.post("/api/psykologen/start", "{}").json();
        assertTrue(start.bool("success"));
        assertEquals("Fejkat svar", start.string("message"));
        assertEquals("erik", start.string("role"));
        assertMatare(start);

        JsonObject svar = browser.post("/api/psykologen/message", "{\"message\":\"Hej\"}").json();
        assertTrue(svar.bool("success"));
        assertEquals("Fejkat svar", svar.string("message"));
        assertFalse(svar.contains("sessionComplete"));
        assertMatare(svar);
    }

    private static void assertMatare(JsonObject response) {
        response.number("elapsedMinutes");
        assertEquals(45.0, response.number("sessionDurationMinutes").doubleValue(), 1e-9);
        assertEquals(0.0, response.number("costUsd").doubleValue(), 1e-9);
        // "fake-modell" finns inte i prislistan.
        assertFalse(response.bool("costComplete"));
    }

    static void samtaletHarRollerMedSmaBokstaverOchSessionTime() throws Exception {
        Browser browser = inloggad();
        browser.post("/api/psykologen/reset", "{}");
        browser.post("/api/psykologen/start", "{}");
        browser.post("/api/psykologen/message", "{\"message\":\"Jag sover dåligt\"}");

        JsonObject samtal = browser.get("/api/psykologen/conversation").json();
        assertTrue(samtal.bool("success"));
        assertMatare(samtal);
        boolean sawUser = false;
        for (JsonValue value : samtal.array("conversation").values()) {
            JsonObject message = (JsonObject) value;
            String role = message.string("role");
            assertTrue(role.equals("user") || role.equals("assistant") || role.equals("system"), role);
            assertFalse(message.contains("sessionTimeMs"), "fältet heter session_time: " + message);
            if (role.equals("user")) {
                sawUser = true;
                assertEquals("Jag sover dåligt", message.string("content"));
                message.number("timestamp");
                message.number("session_time");
            }
        }
        assertTrue(sawUser, samtal.toString());
    }

    static void tomtMeddelandeGer400MedFelText() throws Exception {
        Browser browser = inloggad();

        var reply = browser.post("/api/psykologen/message", "{\"message\":\"   \"}");
        assertEquals(400, reply.status());
        assertFalse(reply.json().bool("success"));
        assertEquals("Message cannot be empty", reply.json().string("error"));

        assertEquals(400, browser.post("/api/psykologen/message", "{}").status());
    }

    static void trasigJsonGer400() throws Exception {
        assertEquals(400, inloggad().post("/api/psykologen/message", "{\"message\":").status());
    }

    static void traceHarAnropOchSammanfattning() throws Exception {
        Browser browser = inloggad();
        browser.post("/api/psykologen/start", "{}");

        JsonObject trace = browser.get("/api/psykologen/trace").json();
        assertTrue(trace.bool("success"));
        JsonObject call = trace.array("calls").object(0);
        assertEquals("fake-modell", call.string("model"));
        assertEquals("Fejkat svar", call.string("responseText"));
        // Okänt pris och lyckat anrop skrivs som null, inte utelämnade.
        assertTrue(call.get("costUsd") instanceof spola.json.JsonNull, call.toString());
        assertTrue(call.get("error") instanceof spola.json.JsonNull, call.toString());
        call.array("sentMessages");
        trace.array("thoughts");
        trace.array("openThreads");
        JsonObject summary = trace.object("summary");
        for (String field : new String[] {"callCount", "totalInputTokens", "totalOutputTokens", "totalCostUsd",
                "avgLatencyMs", "currentContextTokens", "errorCount"}) {
            summary.number(field);
        }
        summary.bool("costComplete");
    }

    static void installningarnaHarPromptarOchStrategier() throws Exception {
        JsonObject settings = inloggad().get("/api/psykologen/settings/prompts").json();

        assertTrue(settings.bool("success"));
        settings.bool("useCustomPrompts");
        assertEquals(8, settings.object("prompts").size());
        assertEquals(8, settings.object("defaults").size());
        assertEquals(45.0, settings.number("defaultSessionDurationMinutes").doubleValue(), 1e-9);
        settings.number("sessionDurationMinutes");
        settings.string("contextStrategy");
        JsonObject strategy = settings.array("contextStrategies").object(0);
        strategy.string("id");
        strategy.string("label");
        strategy.string("description");
    }

    static void installningarSparasOchFelRapporteras() throws Exception {
        Browser browser = inloggad();

        var okand = browser.put("/api/psykologen/settings/prompts", "{\"finnsInte\":\"x\"}");
        assertEquals(400, okand.status());
        assertEquals("Okänd promptnyckel: finnsInte", okand.json().string("error"));

        assertEquals(200, browser.put("/api/psykologen/settings/prompts", "{\"openingInstruction\":\"Säg hej.\"}").status());
        JsonObject settings = browser.get("/api/psykologen/settings/prompts").json();
        assertTrue(settings.bool("useCustomPrompts"));
        assertEquals("Säg hej.", settings.object("prompts").string("openingInstruction"));

        var reset = browser.post("/api/psykologen/settings/prompts/reset", "{\"key\":\"openingInstruction\"}");
        assertEquals(200, reset.status());
        assertFalse(browser.get("/api/psykologen/settings/prompts").json().object("prompts")
                .string("openingInstruction").equals("Säg hej."));

        var enabled = browser.put("/api/psykologen/settings/custom-prompts-enabled", "{\"enabled\":false}");
        assertFalse(enabled.json().bool("enabled"));

        var saknas = browser.put("/api/psykologen/settings/session-duration", "{}");
        assertEquals(400, saknas.status());
        assertEquals("Fältet 'minutes' saknas.", saknas.json().string("error"));
        var noll = browser.put("/api/psykologen/settings/session-duration", "{\"minutes\":0}");
        assertEquals("Sessionslängden måste vara större än 0 minuter.", noll.json().string("error"));
        var trettio = browser.put("/api/psykologen/settings/session-duration", "{\"minutes\":30}");
        assertEquals(30.0, trettio.json().number("sessionDurationMinutes").doubleValue(), 1e-9);

        var strategi = browser.put("/api/psykologen/settings/context-strategy", "{\"strategy\":\"finns-inte\"}");
        assertEquals(400, strategi.status());
        assertEquals("Okänd kontextstrategi: finns-inte", strategi.json().string("error"));
        var utanStrategi = browser.put("/api/psykologen/settings/context-strategy", "{}");
        assertEquals("Fältet 'strategy' saknas.", utanStrategi.json().string("error"));
    }

    static void historikenArEnLista() throws Exception {
        JsonObject history = inloggad().get("/api/psykologen/history").json();

        assertTrue(history.bool("success"));
        history.array("history");
    }
}
