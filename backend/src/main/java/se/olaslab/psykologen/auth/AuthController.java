package se.olaslab.psykologen.auth;

import static spola.json.codec.JsonCodec.field;
import static spola.json.codec.JsonCodecs.STRING;

import java.io.IOException;

import spola.http.Response;
import spola.json.JsonObject;
import spola.json.codec.JsonCodec;
import spola.security.AuthResult;
import spola.security.Authenticator;
import spola.security.Identity;
import spola.security.web.WebSecurity;
import spola.web.Responses;
import spola.web.RouterBuilder;
import spola.web.WebRequest;

/** {@code /api/auth}: inloggning, utloggning och vem som är inloggad. */
public class AuthController {

    private final Authenticator authenticator;
    private final LoginAttemptTracker attemptTracker;

    public AuthController(Authenticator authenticator, LoginAttemptTracker attemptTracker) {
        this.authenticator = authenticator;
        this.attemptTracker = attemptTracker;
    }

    public RouterBuilder routes(RouterBuilder routes) {
        return routes
                .post("/api/auth/login", this::login)
                .post("/api/auth/logout", this::logout)
                .get("/api/auth/me", this::me);
    }

    Response login(WebRequest request) throws IOException {
        Login body = request.readJson(Login.CODEC);
        String username = body.username();

        if (attemptTracker.isLocked(username)) {
            return Responses.json(request, 429, JsonObject.builder()
                    .put("success", false)
                    .put("error", "För många misslyckade försök. Försök igen om "
                            + attemptTracker.minutesRemaining(username) + " minuter.")
                    .build());
        }

        return switch (authenticator.authenticate(username, body.password())) {
            case AuthResult.Authenticated(Identity identity) -> {
                // Nytt sessions-id (skydd mot session fixation) och ny CSRF-token.
                WebSecurity.login(request, identity);
                attemptTracker.recordSuccess(username);
                yield Responses.json(request, 200, JsonObject.builder()
                        .put("success", true)
                        .put("username", identity.name())
                        .build());
            }
            case AuthResult.Failed failed -> {
                attemptTracker.recordFailure(username);
                // Samma svar oavsett om användarnamnet finns - annars går konton att kartlägga.
                yield Responses.json(request, 401, JsonObject.builder()
                        .put("success", false)
                        .put("error", "Fel användarnamn eller lösenord.")
                        .build());
            }
        };
    }

    Response logout(WebRequest request) {
        WebSecurity.logout(request);
        return Responses.json(request, 200, JsonObject.builder().put("success", true).build());
    }

    Response me(WebRequest request) {
        return Responses.json(request, 200, JsonObject.builder()
                .put("success", true)
                .put("username", WebSecurity.requireIdentity(request).name())
                .build());
    }

    /** Ett saknat fält är en tom sträng och okända fält ignoreras, som i Spring-versionen. */
    record Login(String username, String password) {

        static final JsonCodec<Login> CODEC = JsonCodec.object(Login::new,
                field("username", STRING, Login::username).orElse(""),
                field("password", STRING, Login::password).orElse("")).ignoringUnknown();

        /** Utan lösenordet, så att det aldrig hamnar i en loggrad. */
        @Override
        public String toString() {
            return "Login[username=" + username + "]";
        }
    }
}
