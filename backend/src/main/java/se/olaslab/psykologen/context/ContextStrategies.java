package se.olaslab.psykologen.context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registret över valbara strategier. */
public final class ContextStrategies {

    public static final String DEFAULT_ID = FullHistoryStrategy.ID;

    private static final Map<String, ContextStrategy> STRATEGIER = new LinkedHashMap<>();

    static {
        register(new FullHistoryStrategy());
        register(new SlidingWindowStrategy());
        register(new ProfileStrategy());
    }

    private ContextStrategies() {
    }

    private static void register(ContextStrategy strategy) {
        STRATEGIER.put(strategy.id(), strategy);
    }

    /** Okänt id faller tillbaka på standardstrategin - en gammal prompts.json ska inte fälla appen. */
    public static ContextStrategy byId(String id) {
        return STRATEGIER.getOrDefault(id, STRATEGIER.get(DEFAULT_ID));
    }

    public static boolean exists(String id) {
        return STRATEGIER.containsKey(id);
    }

    public static List<ContextStrategy> all() {
        return List.copyOf(STRATEGIER.values());
    }
}
