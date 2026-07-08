package io.github.enpici.villager.life.minecraft;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.Optional;

public class WorldBlockSearch {

    public Optional<Block> nearestBlock(Location origin, Material material, int radius) {
        if (origin == null || origin.getWorld() == null || material == null || radius <= 0) {
            return Optional.empty();
        }

        World world = origin.getWorld();
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        int minY = Math.max(world.getMinHeight(), baseY - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, baseY + radius);

        Block bestExposed = null;
        double bestExposedDistance = Double.MAX_VALUE;
        Block bestFallback = null;
        double bestFallbackDistance = Double.MAX_VALUE;
        for (int x = baseX - radius; x <= baseX + radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != material) {
                        continue;
                    }
                    double distance = block.getLocation().distanceSquared(origin);
                    if (isExposed(block)) {
                        if (distance < bestExposedDistance) {
                            bestExposed = block;
                            bestExposedDistance = distance;
                        }
                    } else if (distance < bestFallbackDistance) {
                        bestFallback = block;
                        bestFallbackDistance = distance;
                    }
                }
            }
        }

        return Optional.ofNullable(bestExposed != null ? bestExposed : bestFallback);
    }

    private boolean isExposed(Block block) {
        for (BlockFace face : BlockFace.values()) {
            if (!face.isCartesian()) {
                continue;
            }
            Material adjacent = block.getRelative(face).getType();
            if (adjacent.isAir()) {
                return true;
            }
        }
        return false;
    }
}
