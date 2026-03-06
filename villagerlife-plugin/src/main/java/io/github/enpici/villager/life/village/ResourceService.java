package io.github.enpici.villager.life.village;

import io.github.enpici.villager.life.VillagerLifePlugin;
import io.github.enpici.villager.life.build.BlockPlacementStep;
import io.github.enpici.villager.life.build.BuildPlan;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ResourceService {

    private final VillageAI village;
    private final Map<Material, List<Material>> substitutions;

    public ResourceService(VillageAI village, Map<Material, List<Material>> substitutions) {
        this.village = village;
        this.substitutions = substitutions;
    }

    public static ResourceService fromPluginConfig(VillageAI village) {
        VillagerLifePlugin plugin = VillagerLifePlugin.instance();
        if (plugin == null) {
            return new ResourceService(village, Map.of());
        }
        Map<Material, List<Material>> rules = new EnumMap<>(Material.class);
        var section = plugin.getConfig().getConfigurationSection("build.material-substitutions");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material source = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
                if (source == null) {
                    continue;
                }
                List<Material> candidates = section.getStringList(key).stream()
                        .map(value -> Material.matchMaterial(value.toUpperCase(Locale.ROOT)))
                        .filter(material -> material != null)
                        .toList();
                if (!candidates.isEmpty()) {
                    rules.put(source, candidates);
                }
            }
        }
        return new ResourceService(village, rules);
    }

    public boolean hasMaterials(BuildPlan plan) {
        return calculateMissingMaterials(plan).isEmpty();
    }

    public boolean reserveMaterials(BuildPlan plan) {
        Map<Material, Integer> temporaryReservations = new HashMap<>();
        for (BlockPlacementStep step : plan.steps()) {
            Material resolved = resolveMaterialForReservation(step.material());
            if (resolved == null || !village.reserveMaterial(resolved, 1)) {
                temporaryReservations.forEach(village::releaseReservedMaterial);
                return false;
            }
            temporaryReservations.merge(resolved, 1, Integer::sum);
        }
        return true;
    }

    public boolean consumeForStep(BlockPlacementStep step) {
        Material exact = step.material();
        if (village.consumeReservedMaterial(exact, 1)) {
            return true;
        }
        for (Material substitute : substitutions.getOrDefault(exact, List.of())) {
            if (village.consumeReservedMaterial(substitute, 1)) {
                return true;
            }
        }
        return false;
    }

    public void releaseUnconsumed(BuildPlan plan, int startIndex) {
        if (startIndex < 0) {
            startIndex = 0;
        }
        for (int index = startIndex; index < plan.size(); index++) {
            BlockPlacementStep step = plan.get(index);
            Material reserved = resolveMaterialForReservation(step.material());
            if (reserved != null) {
                village.releaseReservedMaterial(reserved, 1);
            }
        }
    }

    public Map<Material, Integer> calculateMissingMaterials(BuildPlan plan) {
        Map<Material, Integer> missing = new EnumMap<>(Material.class);
        Map<Material, Integer> available = new EnumMap<>(Material.class);
        village.materialStockSnapshot().forEach((material, amount) -> available.put(material, village.availableMaterial(material)));

        for (BlockPlacementStep step : plan.steps()) {
            Material chosen = findUsableMaterial(step.material(), available);
            if (chosen == null) {
                missing.merge(step.material(), 1, Integer::sum);
                continue;
            }
            available.merge(chosen, -1, Integer::sum);
        }
        return missing;
    }

    private Material resolveMaterialForReservation(Material requested) {
        if (village.availableMaterial(requested) > 0) {
            return requested;
        }
        for (Material substitute : substitutions.getOrDefault(requested, List.of())) {
            if (village.availableMaterial(substitute) > 0) {
                return substitute;
            }
        }
        return null;
    }

    private Material findUsableMaterial(Material requested, Map<Material, Integer> available) {
        if (available.getOrDefault(requested, 0) > 0) {
            return requested;
        }
        for (Material substitute : substitutions.getOrDefault(requested, List.of())) {
            if (available.getOrDefault(substitute, 0) > 0) {
                return substitute;
            }
        }
        return null;
    }
}
