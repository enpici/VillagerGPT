package io.github.enpici.villager.life.planning;

import io.github.enpici.villager.life.build.BlockPlacementStep;
import io.github.enpici.villager.life.build.BuildPlan;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class ShelterBlueprintFactory {

    public BuildPlan create(VillageAI village, ShelterPlan shelterPlan) {
        Location origin = originFor(village, shelterPlan);
        List<BlockPlacementStep> steps = new ArrayList<>();

        int width = shelterPlan.width();
        int height = shelterPlan.height();
        int depth = shelterPlan.depth();
        int doorX = width / 2;
        int doorZ = 0;

        for (int y = 1; y < height - 1; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    if (!isWall(x, z, width, depth)) {
                        continue;
                    }
                    if (shelterPlan.includeDoor() && x == doorX && z == doorZ && (y == 1 || y == 2)) {
                        continue;
                    }
                    addStep(steps, shelterPlan.wallMaterial(), origin.clone().add(x, y, z));
                }
            }
        }

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                addStep(steps, shelterPlan.roofMaterial(), origin.clone().add(x, height - 1, z));
            }
        }

        if (shelterPlan.includeDoor()) {
            addStep(steps, Material.OAK_DOOR, origin.clone().add(doorX, 1, doorZ));
        }
        if (shelterPlan.includeTorches()) {
            addStep(steps, Material.TORCH, origin.clone().add(1, 2, 1));
        }

        return new BuildPlan(steps);
    }

    private Location originFor(VillageAI village, ShelterPlan plan) {
        Location center = village.center();
        int offset = Math.max(4, village.population() + 3);
        return new Location(
                center.getWorld(),
                center.getBlockX() + offset,
                center.getBlockY(),
                center.getBlockZ() + offset
        );
    }

    private boolean isWall(int x, int z, int width, int depth) {
        return x == 0 || z == 0 || x == width - 1 || z == depth - 1;
    }

    private void addStep(List<BlockPlacementStep> steps, Material material, Location location) {
        List<Integer> prerequisites = steps.isEmpty() ? List.of() : List.of(steps.size() - 1);
        steps.add(new BlockPlacementStep(material, location, prerequisites));
    }
}
