package se.olaslab.psykologen.trace;

import java.util.Map;
import java.util.Optional;

/**
 * Pris per miljon tokens, för att kunna sätta en kostnad på varje anrop i Glaslådan.
 *
 * <p>Priserna är hårdkodade och kan bli inaktuella - en modell utan pris här ger inget
 * gissat belopp, utan tom kostnad som UI:t visar som okänd. Hellre ingen siffra än fel siffra.
 */
public final class Prislista {

    private record Pris(double inPerMiljon, double utPerMiljon) {
    }

    private static final Map<String, Pris> PRISER = Map.of(
            "claude-haiku-4-5", new Pris(1.00, 5.00),
            "claude-sonnet-5", new Pris(2.00, 10.00),
            "claude-opus-5", new Pris(5.00, 25.00));

    private Prislista() {
    }

    /**
     * Kostnad i USD, eller tomt för en modell utan känt pris.
     *
     * <p>Cache-tokens prissätts inte separat: appen sätter inga cacheControl-brytpunkter, så
     * de är alltid noll. Slås caching på behöver den här metoden räknas om.
     */
    public static Optional<Double> kostnad(String model, int inputTokens, int outputTokens) {
        Pris pris = PRISER.get(model);
        if (pris == null) {
            return Optional.empty();
        }
        double kostnad = (inputTokens * pris.inPerMiljon() + outputTokens * pris.utPerMiljon()) / 1_000_000.0;
        return Optional.of(kostnad);
    }

    public static boolean harPris(String model) {
        return PRISER.containsKey(model);
    }
}
