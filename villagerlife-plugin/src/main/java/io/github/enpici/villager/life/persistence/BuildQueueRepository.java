package io.github.enpici.villager.life.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class BuildQueueRepository {

    private final Path file;
    private final ObjectMapper objectMapper;

    public BuildQueueRepository(Path baseDir, ObjectMapper objectMapper) {
        this.file = baseDir.resolve("build-queue-state.json");
        this.objectMapper = objectMapper;
    }

    public Optional<BuildQueueStateRecord> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), BuildQueueStateRecord.class));
        } catch (IOException exception) {
            throw new IllegalStateException("Error cargando build-queue-state.json", exception);
        }
    }

    public void save(BuildQueueStateRecord record) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), record);
        } catch (IOException exception) {
            throw new IllegalStateException("Error guardando build-queue-state.json", exception);
        }
    }
}
