package com.example.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Enkel spärr mot lösenordsgissning: efter {@link #MAX_ATTEMPTS} misslyckade
 * försök i rad låses användarnamnet i {@link #LOCKOUT}.
 *
 * Utan den här är ett kort lösenord knäckbart på minuter, vilket hade gjort
 * resten av inloggningen meningslös. Räknaren ligger i minnet och nollställs
 * vid omstart - fullt tillräckligt för en app med en handfull konton, och
 * utan nya beroenden.
 *
 * Räkningen sker per användarnamn, inte per IP: det skyddar ett känt konto
 * mot gissning, men bromsar inte någon som sprider försöken över många
 * användarnamn. För den här appen är det rätt avvägning; ligger den någon
 * gång öppet på internet hör IP-baserad begränsning hemma i en reverse proxy
 * framför.
 */
@Component
public class LoginAttemptTracker {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(5);

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    /** True om användarnamnet är låst just nu och inte ens ska få lösenordet prövat. */
    public boolean isLocked(String username) {
        Attempts current = attempts.get(username);
        if (current == null) {
            return false;
        }
        if (current.count() < MAX_ATTEMPTS) {
            return false;
        }
        if (Instant.now().isAfter(current.lastFailure().plus(LOCKOUT))) {
            attempts.remove(username);
            return false;
        }
        return true;
    }

    public void recordFailure(String username) {
        attempts.compute(username, (key, current) -> {
            int previous = current == null ? 0 : current.count();
            return new Attempts(previous + 1, Instant.now());
        });
    }

    public void recordSuccess(String username) {
        attempts.remove(username);
    }

    /** Minuter kvar av låsningen, avrundat uppåt - för felmeddelandet till användaren. */
    public long minutesRemaining(String username) {
        Attempts current = attempts.get(username);
        if (current == null) {
            return 0;
        }
        Duration left = Duration.between(Instant.now(), current.lastFailure().plus(LOCKOUT));
        return Math.max(1, (left.toSeconds() + 59) / 60);
    }

    private record Attempts(int count, Instant lastFailure) {
    }
}
