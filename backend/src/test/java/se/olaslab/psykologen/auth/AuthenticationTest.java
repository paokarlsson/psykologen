package se.olaslab.psykologen.auth;

import static spola.test.Assertions.assertEquals;
import static spola.test.Assertions.assertFalse;
import static spola.test.Assertions.assertTrue;
import static spola.test.Tests.suite;
import static spola.test.Tests.test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;

import se.olaslab.psykologen.Browser;
import se.olaslab.psykologen.TestApp;
import spola.test.Suite;

/** Inloggningen och åtkomsten genom en riktig server, som frontenden använder den. */
public final class AuthenticationTest {

    private static final String ANNA_PASSWORD = TestApp.ANNA_PASSWORD;
    private static final String BO_PASSWORD = TestApp.BO_PASSWORD;

    private static TestApp app;
    private static URI base;

    private AuthenticationTest() {
    }

    public static Suite tests() {
        return suite("AuthenticationTest",
                test("skyddadEndpointKraverInloggning", AuthenticationTest::skyddadEndpointKraverInloggning),
                test("felLosenordGerUnauthorized", AuthenticationTest::felLosenordGerUnauthorized),
                test("inloggningFungerarUtanBefintligSession", AuthenticationTest::inloggningFungerarUtanBefintligSession),
                test("rattLosenordGerInloggadSession", AuthenticationTest::rattLosenordGerInloggadSession),
                test("inloggningGerNyCsrfTokenOchSessionscookie", AuthenticationTest::inloggningGerNyCsrfTokenOchSessionscookie),
                test("utloggningAvslutarSessionen", AuthenticationTest::utloggningAvslutarSessionen),
                test("postUtanCsrfHuvudNekas", AuthenticationTest::postUtanCsrfHuvudNekas),
                test("femFelforsokSparrarAnvandarnamnet", AuthenticationTest::femFelforsokSparrarAnvandarnamnet),
                test("healthcheckKraverIngenInloggning", AuthenticationTest::healthcheckKraverIngenInloggning),
                test("anvandareSerInteVarandrasProfil", AuthenticationTest::anvandareSerInteVarandrasProfil),
                test("traceKraverInloggning", AuthenticationTest::traceKraverInloggning),
                test("anvandareSerInteVarandrasTrace", AuthenticationTest::anvandareSerInteVarandrasTrace))
                .beforeAll(AuthenticationTest::startServer)
                .afterAll(AuthenticationTest::stopServer);
    }

    static void startServer() throws IOException {
        app = TestApp.start();
        base = app.base();
    }

    static void stopServer() throws IOException {
        app.close();
    }

    static void skyddadEndpointKraverInloggning() throws Exception {
        assertEquals(401, new Browser(base).get("/api/psykologen/profile").status());
    }

    static void felLosenordGerUnauthorized() throws Exception {
        var reply = new Browser(base).login("anna", "fel-losen");

        assertEquals(401, reply.status());
        assertFalse(reply.json().bool("success"));
        assertEquals("Fel användarnamn eller lösenord.", reply.json().string("error"));
    }

    static void inloggningFungerarUtanBefintligSession() throws Exception {
        var reply = new Browser(base).login("anna", ANNA_PASSWORD);

        assertEquals(200, reply.status());
        assertEquals("anna", reply.json().string("username"));
    }

    static void rattLosenordGerInloggadSession() throws Exception {
        var browser = new Browser(base);
        browser.login("anna", ANNA_PASSWORD);

        var me = browser.get("/api/auth/me");
        assertEquals(200, me.status());
        assertTrue(me.json().bool("success"));
        assertEquals("anna", me.json().string("username"));
    }

    static void inloggningGerNyCsrfTokenOchSessionscookie() throws Exception {
        var browser = new Browser(base);
        browser.get("/api/auth/me");
        String tokenBefore = browser.cookies().get("XSRF-TOKEN");

        var reply = browser.login("anna", ANNA_PASSWORD);

        assertTrue(reply.setCookies().stream().anyMatch(c -> c.startsWith("SESSION=") && c.contains("HttpOnly")
                && c.contains("SameSite=Lax")), "sessionscookien: " + reply.setCookies());
        assertTrue(reply.setCookies().stream().anyMatch(c -> c.startsWith("XSRF-TOKEN=") && !c.contains("HttpOnly")),
                "Angular måste kunna läsa CSRF-cookien: " + reply.setCookies());
        assertFalse(tokenBefore.equals(browser.cookies().get("XSRF-TOKEN")), "CSRF-token byts vid inloggning");
    }

    static void utloggningAvslutarSessionen() throws Exception {
        var browser = new Browser(base);
        browser.login("anna", ANNA_PASSWORD);

        var logout = browser.post("/api/auth/logout", "{}");

        assertEquals(200, logout.status());
        assertTrue(logout.json().bool("success"));
        assertEquals(401, browser.get("/api/auth/me").status());
    }

    static void postUtanCsrfHuvudNekas() throws Exception {
        var browser = new Browser(base);
        browser.login("anna", ANNA_PASSWORD);

        assertEquals(403, browser.postWithoutCsrf("/api/psykologen/reset", "{}").status());
        assertEquals(200, browser.post("/api/psykologen/reset", "{}").status());
    }

    static void femFelforsokSparrarAnvandarnamnet() throws Exception {
        var browser = new Browser(base);
        for (int i = 0; i < 5; i++) {
            assertEquals(401, browser.login("gissad", "fel-" + i).status());
        }

        var locked = browser.login("gissad", "fel-igen");

        assertEquals(429, locked.status());
        assertTrue(locked.json().string("error").startsWith("För många misslyckade försök."), locked.body());
    }

    static void healthcheckKraverIngenInloggning() throws Exception {
        var reply = new Browser(base).get("/actuator/health");

        assertEquals(200, reply.status());
        assertEquals("UP", reply.json().string("status"));
    }

    static void anvandareSerInteVarandrasProfil() throws Exception {
        var anna = new Browser(base);
        anna.login("anna", ANNA_PASSWORD);
        var bosse = new Browser(base);
        bosse.login("bosse", BO_PASSWORD);

        // Katalogen finns först efter första anropet: PsykologenService tömmer den när
        // den skapas, så en profil skriven innan dess vore borta.
        assertEquals(200, anna.get("/api/psykologen/profile").status());
        assertEquals(200, bosse.get("/api/psykologen/profile").status());

        Files.writeString(app.storageDir().resolve("anna").resolve("profile.md"), "Annas privata profil");

        assertEquals("Annas privata profil", anna.get("/api/psykologen/profile").json().string("profile"));
        assertEquals("Ingen profil skapad än.", bosse.get("/api/psykologen/profile").json().string("profile"));
    }

    static void traceKraverInloggning() throws Exception {
        assertEquals(401, new Browser(base).get("/api/psykologen/trace").status());
    }

    static void anvandareSerInteVarandrasTrace() throws Exception {
        var anna = new Browser(base);
        anna.login("anna", ANNA_PASSWORD);
        var bosse = new Browser(base);
        bosse.login("bosse", BO_PASSWORD);

        // Traceen innehåller hela samtalet i klartext - den får aldrig läcka mellan konton.
        assertEquals(200, anna.post("/api/psykologen/message", "{\"message\":\"Annas hemlighet\"}").status());

        // Antalet anrop assertas inte: bakgrundsjobben för profil och plan kan hinna klart
        // eller inte innan traceen läses. Det som spelar roll är vem som ser vad.
        var annasTrace = anna.get("/api/psykologen/trace");
        assertEquals(200, annasTrace.status());
        assertFalse(annasTrace.json().array("calls").isEmpty());
        assertTrue(annasTrace.body().contains("Annas hemlighet"), "Annas egen trace ska innehålla det hon skrev");

        var bosTrace = bosse.get("/api/psykologen/trace");
        assertEquals(200, bosTrace.status());
        assertEquals(0, bosTrace.json().object("summary").number("callCount").intValueExact());
        assertFalse(bosTrace.body().contains("Annas hemlighet"), "Bosse ska aldrig se Annas samtal");
    }
}
