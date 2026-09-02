package com.example.storage;

import java.util.List;
import java.util.Optional;

/**
 * Läser och skriver de artefakter en session producerar (patient-profil,
 * sessionsplan, ändringshistorik). Ett interface så lagringen kan bytas ut
 * (t.ex. i test) utan att röra {@link com.example.service.PsykologenService}
 * eller {@link com.example.service.BackgroundSessionUpdater}.
 */
public interface SessionArtifactStore {

    /** Tomt om profilen inte finns eller inte kunde läsas. */
    Optional<String> readProfile();

    void writeProfile(String content);

    /** Tomt om planen inte finns eller inte kunde läsas. */
    Optional<String> readPlan();

    void writePlan(String content);

    /** Loggar en enskild ändring av profilen eller planen i ändringshistoriken. */
    void appendHistory(HistoryEntry entry);

    /** Hela ändringshistoriken, nyast först. */
    List<HistoryEntry> readHistory();

    /** Tar bort eventuella kvarvarande artefakter (profil, plan, historik) från en tidigare session. */
    void clear();
}
