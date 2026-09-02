package com.example.storage;

/**
 * En rad i ändringsloggen för profil eller plan: vad som ändrades och när.
 * Skapas av {@link com.example.service.BackgroundSessionUpdater} från den
 * "===ÄNDRINGAR==="-sektion AI:t skriver vid varje profil-/planuppdatering
 * (se {@link com.example.prompt.ChangelogResponse}).
 */
public record HistoryEntry(String type, long timestamp, double elapsedMinutes, String change) {

    public static final String PROFILE = "profile";
    public static final String PLAN = "plan";
}
