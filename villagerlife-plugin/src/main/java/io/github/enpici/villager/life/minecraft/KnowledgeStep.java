package io.github.enpici.villager.life.minecraft;

import org.bukkit.Material;

import java.util.Map;
import java.util.stream.Collectors;

public record KnowledgeStep(
        Kind kind,
        Material target,
        Material input,
        Material output,
        Material tool,
        Material station,
        Map<Material, Integer> requirements,
        String role,
        String note
) {

    public enum Kind {
        USE_STOCK,
        COLLECT_BLOCK,
        MINE_BLOCK,
        CRAFT_ITEM,
        SMELT_ITEM,
        REQUEST_INPUTS
    }

    public String describe() {
        String base = switch (kind) {
            case USE_STOCK -> "use stock " + material(target);
            case COLLECT_BLOCK -> "collect " + material(input) + " -> " + material(output);
            case MINE_BLOCK -> "mine " + material(input) + " with " + material(tool) + " -> " + material(output);
            case CRAFT_ITEM -> "craft " + material(output) + " from " + formatRequirements(requirements);
            case SMELT_ITEM -> "smelt " + material(input) + " in " + material(station) + " -> " + material(output);
            case REQUEST_INPUTS -> "request " + formatRequirements(requirements);
        };
        return role + ": " + base + (note == null || note.isBlank() ? "" : " (" + note + ")");
    }

    private String material(Material material) {
        return material != null ? material.name().toLowerCase() : "none";
    }

    private String formatRequirements(Map<Material, Integer> values) {
        if (values == null || values.isEmpty()) {
            return "nothing";
        }
        return values.entrySet().stream()
                .map(entry -> entry.getValue() + "x " + entry.getKey().name().toLowerCase())
                .collect(Collectors.joining(", "));
    }
}
