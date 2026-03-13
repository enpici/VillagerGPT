package io.github.enpici.villager.life.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PersistenceMigrations {

    private final Path baseDir;

    public PersistenceMigrations(Path baseDir) {
        this.baseDir = baseDir;
    }

    public void ensureSchema() {
        Path migrationsDir = baseDir.resolve("migrations");
        Path versionFile = migrationsDir.resolve("v1.sql");
        if (Files.exists(versionFile)) {
            return;
        }
        try {
            Files.createDirectories(migrationsDir);
            try (InputStream stream = PersistenceMigrations.class.getResourceAsStream("/persistence/migrations/v1.sql")) {
                if (stream == null) {
                    throw new IllegalStateException("No se encontró migration /persistence/migrations/v1.sql");
                }
                Files.writeString(versionFile, new String(stream.readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo crear el esquema inicial de persistencia", exception);
        }
    }
}
