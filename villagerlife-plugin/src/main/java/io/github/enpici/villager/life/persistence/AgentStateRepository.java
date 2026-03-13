package io.github.enpici.villager.life.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AgentStateRepository {

    private final Path file;
    private final ObjectMapper objectMapper;

    public AgentStateRepository(Path baseDir, ObjectMapper objectMapper) {
        this.file = baseDir.resolve("agent-state.json");
        this.objectMapper = objectMapper;
    }

    public List<AgentStateRecord> load() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(file.toFile(), new TypeReference<>() {});
        } catch (IOException exception) {
            throw new IllegalStateException("Error cargando agent-state.json", exception);
        }
    }

    public void save(List<AgentStateRecord> records) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), records);
        } catch (IOException exception) {
            throw new IllegalStateException("Error guardando agent-state.json", exception);
        }
    }
}
