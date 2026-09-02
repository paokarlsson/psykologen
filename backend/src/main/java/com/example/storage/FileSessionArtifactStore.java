package com.example.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link SessionArtifactStore} som lagrar profil, plan och ändringshistorik
 * som filer i arbetskatalogen (profile.md / plan.md / history.json) - samma
 * platser som användes direkt i {@code PsykologenService} tidigare.
 */
public class FileSessionArtifactStore implements SessionArtifactStore {

    private static final Path PROFILE_PATH = Path.of("profile.md");
    private static final Path PLAN_PATH = Path.of("plan.md");
    private static final Path HISTORY_PATH = Path.of("history.json");

    private final ObjectMapper mapper = new ObjectMapper();
    private final Object historyLock = new Object();

    @Override
    public Optional<String> readProfile() {
        return read(PROFILE_PATH);
    }

    @Override
    public void writeProfile(String content) {
        write(PROFILE_PATH, content);
    }

    @Override
    public Optional<String> readPlan() {
        return read(PLAN_PATH);
    }

    @Override
    public void writePlan(String content) {
        write(PLAN_PATH, content);
    }

    @Override
    public void appendHistory(HistoryEntry entry) {
        synchronized (historyLock) {
            List<HistoryEntry> entries = new ArrayList<>(readHistoryUnlocked());
            entries.add(entry);
            try {
                Files.writeString(HISTORY_PATH, mapper.writeValueAsString(entries));
            } catch (IOException e) {
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
            if (Files.exists(HISTORY_PATH)) {
                return mapper.readValue(Files.readString(HISTORY_PATH), new TypeReference<List<HistoryEntry>>() {
                });
            }
        } catch (IOException e) {
            // Ignorera korrupt/oläsbar fil, kör vidare med tom historik
        }
        return List.of();
    }

    @Override
    public void clear() {
        deleteIfExists(PROFILE_PATH);
        deleteIfExists(PLAN_PATH);
        deleteIfExists(HISTORY_PATH);
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
