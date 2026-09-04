package com.example.prompt;

import java.util.Map;

public final class PromptTemplates {

    private PromptTemplates() {
    }

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
