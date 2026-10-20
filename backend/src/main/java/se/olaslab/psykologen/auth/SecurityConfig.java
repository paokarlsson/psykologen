package se.olaslab.psykologen.auth;

import java.time.Duration;
import java.time.InstantSource;
import java.util.Map;
import java.util.Set;

import spola.security.Authenticator;
import spola.security.BCryptPasswordHasher;
import spola.security.InMemoryUserStore;
import spola.security.PasswordHasher;
import spola.security.User;
import spola.security.web.AccessRule;
import spola.security.web.Csrf;
import spola.security.web.RequestMatcher;
import spola.security.web.WebSecurity;
import spola.web.SessionOptions;
import spola.web.Sessions;

/** Inloggningen med session, CSRF-skyddet för Angular och vilka vägar som är öppna. */
public final class SecurityConfig {

    private SecurityConfig() {
    }

    /**
     * Verifierar {@code {bcrypt}}-hasharna i {@code backend/.env}, också de som Spring skrev, och
     * gör nya i samma format. Kostnad 10, som Springs standard.
     */
    public static PasswordHasher passwordHasher() {
        return PasswordHasher.delegating("bcrypt", Map.of("bcrypt", BCryptPasswordHasher.defaults()));
    }

    public static Authenticator authenticator(AuthProperties properties, PasswordHasher hasher) {
        InMemoryUserStore users = new InMemoryUserStore();
        for (AuthProperties.User configured : properties.getUsers()) {
            users.add(new User(configured.username(), configured.passwordHash(), Set.of("USER"), true));
        }
        return Authenticator.of(users, hasher);
    }

    /**
     * Sessionerna: 30 minuter utan aktivitet, {@code HttpOnly} och {@code SameSite=Lax}.
     *
     * <p>Med {@code secure} (COOKIE_SECURE=true, som compose.prod.yaml sätter) heter cookien
     * {@code __Host-SESSION} och har {@code Secure} på varje svar. Då beror det inte på att
     * proxyhuvudena från Caddy går fram, och en annan underdomän kan inte sätta cookien åt oss.
     * Utan {@code secure} heter den {@code SESSION}, så att den fungerar över
     * {@code http://localhost} under utveckling.
     */
    public static Sessions sessions(boolean secure, InstantSource clock) {
        return Sessions.inMemory(SessionOptions.DEFAULT
                .withIdleTimeout(Duration.ofMinutes(30))
                .withCookieName(secure ? "__Host-SESSION" : "SESSION")
                .withCookie(cookie -> cookie.secure(secure)), clock);
    }

    /**
     * Inloggning, utloggning och Dockers healthcheck är öppna, allt annat kräver inloggning.
     * Varje anrop som ändrar något kräver CSRF-huvudet: Angular läser cookien
     * {@code XSRF-TOKEN} och skickar tillbaka den i {@code X-XSRF-TOKEN}. En oinloggad begäran
     * får 401, aldrig en redirect, eftersom det här är ett API.
     */
    public static WebSecurity security(Sessions sessions, boolean secure) {
        return WebSecurity.builder()
                .sessions(sessions)
                .csrf(Csrf.cookie().withCookie(cookie -> cookie.secure(secure)))
                .rule(RequestMatcher.exactly("/api/auth/login"), AccessRule.permitAll())
                .rule(RequestMatcher.exactly("/api/auth/logout"), AccessRule.permitAll())
                // Dockers healthcheck har ingen session. Proxyn skickar bara /api/* hit, så
                // vägen syns aldrig utifrån.
                .rule(RequestMatcher.exactly("/actuator/health"), AccessRule.permitAll())
                .rule(RequestMatcher.anyRequest(), AccessRule.authenticated())
                .build();
    }
}
