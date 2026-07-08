package io.github.enpici.villager.life.planning;

import org.bukkit.Material;

public record ShelterPlan(
        int width,
        int height,
        int depth,
        Material wallMaterial,
        Material roofMaterial,
        boolean includeDoor,
        boolean includeTorches,
        String source,
        String reason
) {
    public static ShelterPlan fallback() {
        return new ShelterPlan(
                5,
                4,
                5,
                Material.OAK_PLANKS,
                Material.OAK_PLANKS,
                true,
                false,
                "fallback",
                "safe default shelter"
        );
    }
}
