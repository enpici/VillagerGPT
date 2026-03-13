package io.github.enpici.villager.life.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class VillageStateRepository {

    private final Path file;
    private final ObjectMapper objectMapper;

    public VillageStateRepository(Path baseDir, ObjectMapper objectMapper) {
        this.file = baseDir.resolve("village-state.json");
        this.objectMapper = objectMapper;
    }

    public Optional<VillageStateRecord> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), VillageStateRecord.class));
        } catch (IOException exception) {
            throw new IllegalStateException("Error cargando village-state.json", exception);
        }
    }

    public void save(VillageStateRecord record) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), record);
        } catch (IOException exception) {
            throw new IllegalStateException("Error guardando village-state.json", exception);
        }
    }
}
