package com.example.storage;

import java.util.Optional;

/**
 * Läser och skriver de artefakter en session producerar (patient-profil,
 * sessionsplan). Ett interface så lagringen kan bytas ut (t.ex. i test)
 * utan att röra {@link com.example.service.PsykologenService} eller
 * {@link com.example.service.BackgroundSessionUpdater}.
 */
public interface SessionArtifactStore {

    /** Tomt om profilen inte finns eller inte kunde läsas. */
    Optional<String> readProfile();

    void writeProfile(String content);

    /** Tomt om planen inte finns eller inte kunde läsas. */
    Optional<String> readPlan();

    void writePlan(String content);

    /** Tar bort eventuella kvarvarande artefakter från en tidigare session. */
    void clear();
}
