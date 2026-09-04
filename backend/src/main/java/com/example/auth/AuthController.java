package com.example.auth;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Inloggning, utloggning och "vem är jag?".
 *
 * Egna endpoints i stället för Spring Securitys inbyggda {@code formLogin}:
 * den svarar med redirects, vilket passar en server-renderad app men inte en
 * SPA som vill ha JSON och statuskoder.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final LoginAttemptTracker attemptTracker;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, LoginAttemptTracker attemptTracker) {
        this.authenticationManager = authenticationManager;
        this.attemptTracker = attemptTracker;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body,
            HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> result = new HashMap<>();
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");

        if (attemptTracker.isLocked(username)) {
            result.put("success", false);
            result.put("error", "För många misslyckade försök. Försök igen om "
                    + attemptTracker.minutesRemaining(username) + " minuter.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(result);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException e) {
            attemptTracker.recordFailure(username);
            result.put("success", false);
            // Medvetet samma svar oavsett om användarnamnet finns eller inte -
            // annars går kontonamn att kartlägga genom att prova sig fram.
            result.put("error", "Fel användarnamn eller lösenord.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        // Nytt sessions-id vid inloggning, så ett id som en angripare hunnit
        // plantera i webbläsaren inte blir giltigt efteråt. Bara om det finns
        // en session att byta ut - changeSessionId() kastar annars, och en
        // session som skapas först vid saveContext() nedan är ändå ny.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        attemptTracker.recordSuccess(username);
        result.put("success", true);
        result.put("username", authentication.getName());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Vem den nuvarande sessionen tillhör. Frontend anropar den vid
     * sidladdning för att avgöra om en giltig session redan finns, i stället
     * för att visa inloggningsformuläret för någon som redan är inloggad.
     * Kräver inloggning, så en utgången session ger 401 här.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        return ResponseEntity.ok(Map.of("success", true, "username", authentication.getName()));
    }
}
