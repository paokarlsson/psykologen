package se.olaslab.psykologen.storage;

import java.util.List;
import java.util.Optional;

public interface SessionArtifactStore {

    Optional<String> readProfile();

    void writeProfile(String content);

    Optional<String> readPlan();

    void writePlan(String content);

    void appendHistory(HistoryEntry entry);

    List<HistoryEntry> readHistory();

    void clear();
}
