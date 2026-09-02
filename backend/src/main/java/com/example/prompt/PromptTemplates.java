package com.example.prompt;

import java.util.Map;

/**
 * Fyller i de redigerbara promptmallarna från {@link PromptStore} med
 * konkreta värden. Mallarna använder {@code {{namn}}}-platshållare istället
 * för {@code String.format}s positionella {@code %s} - dels så ordningen
 * inte spelar någon roll, dels så att en mall som redigerats i GUI:t (t.ex.
 * med en platshållare borttagen eller kvarglömd) aldrig kan krascha ett
 * AI-anrop, bara resultera i lite sämre kontext.
 *
 * Ren textgenerering - inga sidoeffekter, inga AI-anrop, ingen I/O.
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    /** Ersätter varje {@code {{key}}} i mallen med motsvarande värde ur `vars`. */
    public static String render(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    public static String thoughtReflection(String template, String userInput, String currentThoughts) {
        return render(template, Map.of(
                "userInput", userInput,
                "currentThoughts", currentThoughts.isEmpty() ? "Inga tidigare tankar." : currentThoughts));
    }

    public static String erikResponse(String template, String currentThoughts, String sessionPlan,
            double sessionTimeMinutes, double sessionDurationMinutes, String userInput) {
        double remainingMinutes = Math.max(0, sessionDurationMinutes - sessionTimeMinutes);
        return render(template, Map.of(
                "currentThoughts", currentThoughts,
                "sessionPlan", sessionPlan.isEmpty() ? "Ingen plan än." : sessionPlan,
                "sessionTimeMinutes", String.format("%.1f", sessionTimeMinutes),
                "sessionDurationMinutes", String.format("%.0f", sessionDurationMinutes),
                "remainingMinutes", String.format("%.1f", remainingMinutes),
                "userInput", userInput));
    }

    public static String profileUpdate(String template, String existingProfile, String userInput,
            String agentResponse) {
        return render(template, Map.of(
                "existingProfile", existingProfile.isEmpty() ? "Ingen befintlig profil." : existingProfile,
                "userInput", userInput,
                "agentResponse", agentResponse));
    }

    public static String planUpdate(String template, String existingPlan, String userInput, String agentResponse,
            String timingAnalysis, double elapsedMinutes, double sessionDurationMinutes) {
        double remainingMinutes = Math.max(0, sessionDurationMinutes - elapsedMinutes);
        return render(template, Map.of(
                "existingPlan", existingPlan.isEmpty() ? "Ingen befintlig plan." : existingPlan,
                "userInput", userInput,
                "agentResponse", agentResponse,
                "timingAnalysis", timingAnalysis,
                "elapsedMinutes", String.format("%.1f", elapsedMinutes),
                "sessionDurationMinutes", String.format("%.0f", sessionDurationMinutes),
                "remainingMinutes", String.format("%.1f", remainingMinutes)));
    }
}
