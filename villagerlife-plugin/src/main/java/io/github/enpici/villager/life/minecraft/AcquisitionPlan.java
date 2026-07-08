package io.github.enpici.villager.life.minecraft;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record AcquisitionPlan(
        Material target,
        List<KnowledgeStep> steps,
        Map<Material, Integer> missingInputs
) {

    public boolean satisfiedByStock() {
        return steps.stream().allMatch(step -> step.kind() == KnowledgeStep.Kind.USE_STOCK);
    }

    public List<String> describeLines() {
        if (steps.isEmpty()) {
            return List.of("No known acquisition path for " + target.name().toLowerCase());
        }
        return steps.stream().map(KnowledgeStep::describe).toList();
    }
}
