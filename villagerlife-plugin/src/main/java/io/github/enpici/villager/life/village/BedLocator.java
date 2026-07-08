package io.github.enpici.villager.life.village;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Bed;

import java.util.ArrayList;
import java.util.List;

public class BedLocator {

    public List<Location> findBedHeads(Location center, int radius) {
        List<Location> beds = new ArrayList<>();
        if (center == null || center.getWorld() == null || radius <= 0) {
            return beds;
        }

        int minY = Math.max(center.getWorld().getMinHeight(), center.getBlockY() - 16);
        int maxY = Math.min(center.getWorld().getMaxHeight() - 1, center.getBlockY() + 16);
        for (int x = -radius; x <= radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.getWorld().getBlockAt(center.getBlockX() + x, y, center.getBlockZ() + z);
                    if (isBedHead(block)) {
                        beds.add(block.getLocation());
                    }
                }
            }
        }
        return beds;
    }

    public List<Location> placeStarterBeds(Location center, int count) {
        List<Location> placed = new ArrayList<>();
        if (center == null || center.getWorld() == null || count <= 0) {
            return placed;
        }

        for (int index = 0; index < count; index++) {
            Location foot = center.clone().add(index * 2 - count + 1, 0, 4);
            if (placeBed(foot, BlockFace.SOUTH)) {
                placed.add(foot.getBlock().getRelative(BlockFace.SOUTH).getLocation());
            }
        }
        return placed;
    }

    public String format(Location location) {
        if (location == null || location.getWorld() == null) {
            return "missing";
        }
        return location.getWorld().getName()
                + ":" + location.getBlockX()
                + "," + location.getBlockY()
                + "," + location.getBlockZ();
    }

    private boolean placeBed(Location footLocation, BlockFace facing) {
        Block foot = footLocation.getBlock();
        Block head = foot.getRelative(facing);
        if (!canReplace(foot) || !canReplace(head)) {
            return false;
        }

        foot.setType(Material.RED_BED, false);
        head.setType(Material.RED_BED, false);

        BlockData footData = Material.RED_BED.createBlockData();
        if (footData instanceof Bed bed) {
            bed.setPart(Bed.Part.FOOT);
        }
        if (footData instanceof Directional directional) {
            directional.setFacing(facing);
        }
        foot.setBlockData(footData, false);

        BlockData headData = Material.RED_BED.createBlockData();
        if (headData instanceof Bed bed) {
            bed.setPart(Bed.Part.HEAD);
        }
        if (headData instanceof Directional directional) {
            directional.setFacing(facing);
        }
        head.setBlockData(headData, false);
        return true;
    }

    private boolean canReplace(Block block) {
        return block.getType().isAir()
                || isBed(block.getType());
    }

    private boolean isBedHead(Block block) {
        if (!isBed(block.getType())) {
            return false;
        }
        if (block.getBlockData() instanceof Bed bed) {
            return bed.getPart() == Bed.Part.HEAD;
        }
        return true;
    }

    private boolean isBed(Material material) {
        return material != null && material.name().endsWith("_BED");
    }
}
