package se.olaslab.psykologen.prompt;

import java.util.Map;

import se.olaslab.psykologen.intervention.Intervention;

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

    public static String interventionChoice(String template, String interventionList, String currentThoughts,
            String sessionPlan, double elapsedMinutes, double sessionDurationMinutes, String userInput) {
        double remainingMinutes = Math.max(0, sessionDurationMinutes - elapsedMinutes);
        return render(template, Map.of(
                "interventionList", interventionList,
                "currentThoughts", currentThoughts.isEmpty() ? "Inga tidigare tankar." : currentThoughts,
                "sessionPlan", sessionPlan.isEmpty() ? "Ingen plan än." : sessionPlan,
                "elapsedMinutes", String.format("%.1f", elapsedMinutes),
                "sessionDurationMinutes", String.format("%.0f", sessionDurationMinutes),
                "remainingMinutes", String.format("%.1f", remainingMinutes),
                "userInput", userInput));
    }

    public static String erikResponse(String template, String currentThoughts, String patientProfile,
            String sessionPlan, Intervention intervention, double sessionTimeMinutes,
            double sessionDurationMinutes, String userInput) {
        double remainingMinutes = Math.max(0, sessionDurationMinutes - sessionTimeMinutes);
        return render(template, Map.of(
                "currentThoughts", currentThoughts,
                "patientProfile", patientProfile.isEmpty() ? "Ingen profil än." : patientProfile,
                "sessionPlan", sessionPlan.isEmpty() ? "Ingen plan än." : sessionPlan,
                "intervention", intervention.label() + " - " + intervention.instruction(),
                "sessionTimeMinutes", String.format("%.1f", sessionTimeMinutes),
                "sessionDurationMinutes", String.format("%.0f", sessionDurationMinutes),
                "remainingMinutes", String.format("%.1f", remainingMinutes),
                "userInput", userInput));
    }

    /** Profilen skrivs före Eriks svar, så utdraget är hans föregående replik plus patientens nya. */
    public static String profileUpdate(String template, String existingProfile, String previousResponse,
            String userInput) {
        return render(template, Map.of(
                "existingProfile", existingProfile.isEmpty() ? "Ingen befintlig profil." : existingProfile,
                "previousResponse", previousResponse.isEmpty() ? "Inget tidigare svar från Erik." : previousResponse,
                "userInput", userInput));
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
