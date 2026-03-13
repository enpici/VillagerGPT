package io.github.enpici.villager.life.persistence;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record VillageStateRecord(
        UUID id,
        String name,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        int foodStock,
        int bedCount,
        int populationTarget,
        int maxPopulation,
        long lastThreatTick,
        long lastReproductionTick,
        Map<String, Integer> materialStock,
        Map<String, Integer> reservedMaterials,
        Map<String, Integer> pendingMaterials,
        List<String> pendingQuickBlueprints,
        List<String> pendingLongBlueprints,
        List<MaterialRequestRecord> pendingMaterialRequests
) {
    public record MaterialRequestRecord(String material, int amount) {
    }
}
