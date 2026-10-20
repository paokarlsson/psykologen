package se.olaslab.psykologen.spolabehov.retry;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongFunction;

import spola.client.RestClientException;
import spola.client.RestClientResponseException;
import spola.client.RestClientTransportException;

/**
 * Försöker igen när ett anrop med {@code spola.client} misslyckas tillfälligt, som Anthropics
 * Java-SDK gjorde innan appen gick över till Spola: vid 408, 409, 429, 5xx (också 529,
 * "overloaded") och när ingen anslutning gick att få. En tidsgräns som gått ut försöks inte
 * igen, eftersom anropet redan har väntat hela tidsgränsen.
 *
 * <p>Väntetiden är den servern anger i {@code retry-after-ms} eller {@code retry-after} (högst
 * en minut), annars 0,5 s, 1 s, 2 s och så vidare upp till 8 s, med lite slump så att flera
 * trådar inte försöker samtidigt.
 *
 * <p>Tillfällig lösning tills Spola har en motsvarighet till Spring Retry. Behovet ligger i
 * Spola-repot, {@code uppdrag/behov/2026-10-19-omforsok.md}.
 */
public final class Retry {

    private static final Duration MAX_SERVER_DELAY = Duration.ofMinutes(1);
    private static final Duration FIRST_DELAY = Duration.ofMillis(500);
    private static final Duration MAX_DELAY = Duration.ofSeconds(8);

    /** Väntar; testerna byter ut den så att de inte behöver vänta på riktigt. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final int maxRetries;
    private final Sleeper sleeper;

    public Retry(int maxRetries, Sleeper sleeper) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries får inte vara negativt: " + maxRetries);
        }
        this.maxRetries = maxRetries;
        this.sleeper = sleeper;
    }

    /** Två omförsök, som SDK:ns standard. */
    public static Retry defaults() {
        return new Retry(2, duration -> Thread.sleep(duration));
    }

    public <T> T call(Callable<T> call) throws Exception {
        for (int retry = 0;; retry++) {
            try {
                return call.call();
            } catch (RestClientException e) {
                if (retry >= maxRetries || !isTransient(e)) {
                    throw e;
                }
                int attempt = retry;
                sleeper.sleep(serverDelay(e).orElseGet(() -> backoff(attempt)));
            }
        }
    }

    static boolean isTransient(RestClientException e) {
        return switch (e) {
            case RestClientResponseException response -> {
                int status = response.status();
                yield status == 408 || status == 409 || status == 429 || status >= 500;
            }
            case RestClientTransportException transport -> !transport.timedOut();
        };
    }

    private static Optional<Duration> serverDelay(RestClientException e) {
        if (!(e instanceof RestClientResponseException response)) {
            return Optional.empty();
        }
        var headers = response.headers();
        return headers.firstValue("retry-after-ms").flatMap(value -> parse(value, Duration::ofMillis))
                .or(() -> headers.firstValue("retry-after").flatMap(value -> parse(value, Duration::ofSeconds)))
                .filter(delay -> !delay.isNegative() && delay.compareTo(MAX_SERVER_DELAY) <= 0);
    }

    private static Optional<Duration> parse(String value, LongFunction<Duration> unit) {
        try {
            return Optional.of(unit.apply(Long.parseLong(value.strip())));
        } catch (NumberFormatException e) {
            // Ett datum i Retry-After: vänta som vanligt i stället
            return Optional.empty();
        }
    }

    private static Duration backoff(int retry) {
        Duration delay = FIRST_DELAY.multipliedBy(1L << Math.min(retry, 4));
        if (delay.compareTo(MAX_DELAY) > 0) {
            delay = MAX_DELAY;
        }
        double jitter = 1 - 0.25 * ThreadLocalRandom.current().nextDouble();
        return Duration.ofMillis((long) (delay.toMillis() * jitter));
    }
}
