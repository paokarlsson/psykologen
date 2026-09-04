package com.example.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spärr mot lösenordsgissning. Räknas per användarnamn, inte per IP - IP-spärr hör
 * hemma i en reverse proxy framför.
 */
public class LoginAttemptTracker {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(5);

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

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
