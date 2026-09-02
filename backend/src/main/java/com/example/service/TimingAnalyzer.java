package com.example.service;

import java.util.List;

import com.example.session.ChatMessage;
import com.example.session.Role;

/**
 * Bygger en textsammanfattning av samtalets tidslinje, som skickas med i
 * prompten för sessionsplan-uppdateringen så AI:n kan bedöma tempo och
 * engagemang.
 */
final class TimingAnalyzer {

    private TimingAnalyzer() {
    }

    static String describe(List<ChatMessage> messages) {
        StringBuilder analysis = new StringBuilder("SAMTALSHISTORIK MED TIDSSTÄMPLAR:\n");

        for (int i = 1; i < messages.size(); i++) { // Skip system message
            ChatMessage message = messages.get(i);
            String role = message.role() == Role.USER ? "Patient" : "Erik";
            double sessionMinutes = message.sessionTimeMs() / 60000.0;
            String content = message.content();
            String preview = content.length() > 50 ? content.substring(0, 50) + "..." : content;
            analysis.append(String.format("%d. [%.1fmin] %s: %s\n", i, sessionMinutes, role, preview));
        }

        return analysis.toString();
    }
}
