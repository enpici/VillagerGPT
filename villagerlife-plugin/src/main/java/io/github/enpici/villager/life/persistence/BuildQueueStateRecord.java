package io.github.enpici.villager.life.persistence;

import java.util.List;

public record BuildQueueStateRecord(
        List<String> quickBlueprints,
        List<String> longBlueprints,
        List<MaterialQueueRecord> pendingMaterials
) {
    public record MaterialQueueRecord(String material, int amount) {
    }
}
