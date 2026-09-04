package com.example.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link SessionArtifactStore} som lagrar profil, plan och ändringshistorik
 * som filer (profile.md / plan.md / history.json) i en angiven baskatalog.
 *
 * Baskatalogen är per användare - {@code data/users/&lt;användarnamn&gt;} - så
 * två inloggade användare aldrig kan läsa eller skriva över varandras
 * artefakter. Användarnamnet valideras vid uppstart av
 * {@link com.example.auth.AuthProperties}, så det som når {@code resolve()}
 * här kan aldrig innehålla katalogtraversering.
 */
public class FileSessionArtifactStore implements SessionArtifactStore {

    private final Path profilePath;
    private final Path planPath;
    private final Path historyPath;

    /**
     * Jackson 3 (det Spring Boot 4 använder) kastar {@link JacksonException}
     * okontrollerat, medan {@link java.nio.file.Files} fortfarande kastar
     * {@link IOException} - därför fångas båda där JSON läses och skrivs.
     */
    private final ObjectMapper mapper = new ObjectMapper();
    private final Object historyLock = new Object();

    public FileSessionArtifactStore(Path baseDir) {
        this.profilePath = baseDir.resolve("profile.md");
        this.planPath = baseDir.resolve("plan.md");
        this.historyPath = baseDir.resolve("history.json");
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Kunde inte skapa lagringskatalogen " + baseDir, e);
        }
    }

    @Override
    public Optional<String> readProfile() {
        return read(profilePath);
    }

    @Override
    public void writeProfile(String content) {
        write(profilePath, content);
    }

    @Override
    public Optional<String> readPlan() {
        return read(planPath);
    }

    @Override
    public void writePlan(String content) {
        write(planPath, content);
    }

    @Override
    public void appendHistory(HistoryEntry entry) {
        synchronized (historyLock) {
            List<HistoryEntry> entries = new ArrayList<>(readHistoryUnlocked());
            entries.add(entry);
            try {
                Files.writeString(historyPath, mapper.writeValueAsString(entries));
            } catch (IOException | JacksonException e) {
                // Tyst felhantering - historikloggning får aldrig krascha samtalet
            }
        }
    }

    @Override
    public List<HistoryEntry> readHistory() {
        synchronized (historyLock) {
            return readHistoryUnlocked().stream()
                    .sorted(Comparator.comparingLong(HistoryEntry::timestamp).reversed())
                    .toList();
        }
    }

    private List<HistoryEntry> readHistoryUnlocked() {
        try {
            if (Files.exists(historyPath)) {
                return mapper.readValue(Files.readString(historyPath), new TypeReference<List<HistoryEntry>>() {
                });
            }
        } catch (IOException | JacksonException e) {
            // Ignorera korrupt/oläsbar fil, kör vidare med tom historik
        }
        return List.of();
    }

    @Override
    public void clear() {
        deleteIfExists(profilePath);
        deleteIfExists(planPath);
        deleteIfExists(historyPath);
    }

    private Optional<String> read(Path path) {
        try {
            if (Files.exists(path)) {
                return Optional.of(Files.readString(path));
            }
        } catch (IOException e) {
            // Ignorera läsfel, precis som tidigare
        }
        return Optional.empty();
    }

    private void write(Path path, String content) {
        try {
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Ignorera städfel, precis som tidigare
        }
    }
}
