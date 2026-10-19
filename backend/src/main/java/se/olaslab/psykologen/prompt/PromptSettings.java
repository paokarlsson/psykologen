package se.olaslab.psykologen.prompt;

import static spola.json.codec.JsonCodec.field;
import static spola.json.codec.JsonCodecs.BOOLEAN;
import static spola.json.codec.JsonCodecs.DOUBLE;
import static spola.json.codec.JsonCodecs.STRING;
import static spola.json.codec.JsonCodecs.list;
import static spola.json.codec.JsonCodecs.map;

import java.util.List;
import java.util.Map;

import spola.json.codec.JsonCodec;

/** Det inställningssidan visar: promptarna, standardvärdena, sessionslängden och kontextstrategierna. */
public record PromptSettings(
        boolean useCustomPrompts,
        Map<String, String> prompts,
        Map<String, String> defaults,
        double sessionDurationMinutes,
        double defaultSessionDurationMinutes,
        String contextStrategy,
        List<StrategyOption> contextStrategies) {

    public static final JsonCodec<PromptSettings> CODEC = JsonCodec.object(PromptSettings::new,
            field("useCustomPrompts", BOOLEAN, PromptSettings::useCustomPrompts),
            field("prompts", map(STRING), PromptSettings::prompts),
            field("defaults", map(STRING), PromptSettings::defaults),
            field("sessionDurationMinutes", DOUBLE, PromptSettings::sessionDurationMinutes),
            field("defaultSessionDurationMinutes", DOUBLE, PromptSettings::defaultSessionDurationMinutes),
            field("contextStrategy", STRING, PromptSettings::contextStrategy),
            field("contextStrategies", list(StrategyOption.CODEC), PromptSettings::contextStrategies));

    public record StrategyOption(String id, String label, String description) {

        public static final JsonCodec<StrategyOption> CODEC = JsonCodec.object(StrategyOption::new,
                field("id", STRING, StrategyOption::id),
                field("label", STRING, StrategyOption::label),
                field("description", STRING, StrategyOption::description));
    }
}
