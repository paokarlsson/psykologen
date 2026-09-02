package com.example.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * {@link SessionArtifactStore} som lagrar profil och plan som markdown-
 * filer i arbetskatalogen (profile.md / plan.md) - samma platser som
 * användes direkt i {@code PsykologenService} tidigare.
 */
public class FileSessionArtifactStore implements SessionArtifactStore {

    private static final Path PROFILE_PATH = Path.of("profile.md");
    private static final Path PLAN_PATH = Path.of("plan.md");

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
    public void clear() {
        deleteIfExists(PROFILE_PATH);
        deleteIfExists(PLAN_PATH);
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
