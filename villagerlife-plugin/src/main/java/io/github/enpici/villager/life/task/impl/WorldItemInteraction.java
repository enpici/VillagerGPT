package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.PhysicalResourceScanner;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

final class WorldItemInteraction {

    private static final PhysicalResourceScanner RESOURCE_SCANNER = new PhysicalResourceScanner();

    private WorldItemInteraction() {
    }

    static TaskStatus collectNearbyItem(Agent agent, VillageAI village, double radius, Predicate<Material> accepted) {
        Villager villager = TaskMovement.villager(agent);
        if (villager == null) {
            return TaskStatus.FAILED;
        }
        Optional<Item> candidate = nearestItem(villager, radius, accepted);
        if (candidate.isEmpty()) {
            return TaskStatus.RETRYABLE_FAILED;
        }
        Item item = candidate.get();
        Location target = item.getLocation();
        if (!TaskMovement.reached(villager, target, 2.25)) {
            TaskMovement.moveTo(villager, target, 0.95);
            agent.setLastEvent("world:moving_to_item:" + item.getItemStack().getType().name().toLowerCase());
            return TaskStatus.RUNNING;
        }

        int picked = addStackToVillager(villager, item.getItemStack());
        if (picked <= 0) {
            agent.setLastEvent("world:inventory_full");
            return TaskStatus.RETRYABLE_FAILED;
        }
        Material material = item.getItemStack().getType();
        village.addMaterialStock(material, picked);
        addFoodIfEdible(village, material, picked);

        int remaining = item.getItemStack().getAmount() - picked;
        if (remaining <= 0) {
            item.remove();
        } else {
            ItemStack leftover = item.getItemStack();
            leftover.setAmount(remaining);
            item.setItemStack(leftover);
        }
        agent.setLastEvent("world:picked_item:" + material.name().toLowerCase() + "x" + picked);
        return TaskStatus.SUCCESS;
    }

    static TaskStatus harvestMatureCrop(Agent agent, VillageAI village, int radius) {
        Villager villager = TaskMovement.villager(agent);
        Location origin = villager != null ? villager.getLocation() : village.center();
        Optional<Block> candidate = nearestMatureCrop(origin, radius);
        if (candidate.isEmpty()) {
            return TaskStatus.RETRYABLE_FAILED;
        }

        Block crop = candidate.get();
        Location target = crop.getLocation().add(0.5, 0.0, 0.5);
        if (villager != null && !TaskMovement.reached(villager, target, 2.25)) {
            TaskMovement.moveTo(villager, target, 0.85);
            agent.setLastEvent("world:moving_to_crop:" + crop.getType().name().toLowerCase());
            return TaskStatus.RUNNING;
        }

        Material item = cropDrop(crop.getType());
        int amount = cropYield(crop.getType());
        if (villager != null) {
            addStackToVillager(villager, new ItemStack(item, amount));
        }
        village.addMaterialStock(item, amount);
        addFoodIfEdible(village, item, amount);
        replant(crop);
        agent.setLastEvent("world:harvested:" + item.name().toLowerCase() + "x" + amount);
        return TaskStatus.SUCCESS;
    }

    static int addStackToVillager(Villager villager, ItemStack stack) {
        if (villager == null || stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return 0;
        }
        int requested = stack.getAmount();
        ItemStack copy = stack.clone();
        Map<Integer, ItemStack> leftovers = villager.getInventory().addItem(copy);
        int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        return requested - leftoverAmount;
    }

    static TaskStatus depositInventoryToNearestContainer(Agent agent, VillageAI village, int radius) {
        return depositInventoryToNearestContainer(agent, village, radius, material -> true);
    }

    static TaskStatus depositInventoryToNearestContainer(Agent agent, VillageAI village, int radius, Predicate<Material> accepted) {
        Villager villager = TaskMovement.villager(agent);
        if (villager == null) {
            return TaskStatus.FAILED;
        }
        Optional<Container> container = nearestContainer(village.center(), radius);
        if (container.isEmpty()) {
            agent.setLastEvent("world:no_storage_container");
            return TaskStatus.RETRYABLE_FAILED;
        }
        Location target = container.get().getLocation().add(0.5, 0.0, 0.5);
        if (!TaskMovement.reached(villager, target, 4.0)) {
            TaskMovement.moveTo(villager, target, 0.85);
            agent.setLastEvent("world:moving_to_storage");
            return TaskStatus.RUNNING;
        }

        int moved = moveInventory(villager.getInventory(), container.get().getInventory(), accepted);
        if (moved <= 0) {
            agent.setLastEvent("world:nothing_to_deposit");
            return TaskStatus.RETRYABLE_FAILED;
        }
        agent.setLastEvent("world:deposited_items:" + moved);
        return TaskStatus.SUCCESS;
    }

    static int addGatheredItem(Agent agent, VillageAI village, Material material, int amount) {
        if (material == null || amount <= 0) {
            return 0;
        }
        Villager villager = TaskMovement.villager(agent);
        int stored = villager != null ? addStackToVillager(villager, new ItemStack(material, amount)) : amount;
        if (stored <= 0) {
            return 0;
        }
        village.addMaterialStock(material, stored);
        addFoodIfEdible(village, material, stored);
        return stored;
    }

    static boolean hasCarriedItems(Agent agent) {
        return hasCarriedItems(agent, material -> true);
    }

    static boolean hasCarriedItems(Agent agent, Predicate<Material> accepted) {
        Villager villager = TaskMovement.villager(agent);
        if (villager == null) {
            return false;
        }
        for (ItemStack item : villager.getInventory().getContents()) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0
                    && (accepted == null || accepted.test(item.getType()))) {
                return true;
            }
        }
        return false;
    }

    static boolean isTool(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.endsWith("_PICKAXE")
                || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || name.endsWith("_SWORD");
    }

    static int availableItemCount(Agent agent, VillageAI village, Material material, int radius) {
        if (material == null) {
            return 0;
        }
        if (village.center().getWorld() == null) {
            return village.availableMaterial(material);
        }
        return RESOURCE_SCANNER.scan(village, village.agentManager().all(), radius)
                .materials()
                .getOrDefault(material, 0);
    }

    static boolean consumeItems(Agent agent, VillageAI village, Material material, int amount, int radius) {
        if (material == null || amount <= 0) {
            return false;
        }
        if (village.center().getWorld() == null) {
            return village.consumeMaterial(material, amount);
        }
        return RESOURCE_SCANNER.consumeMaterial(village, village.agentManager().all(), agent, material, amount, radius);
    }

    static void addFoodIfEdible(VillageAI village, Material material, int amount) {
        int food = switch (material) {
            case WHEAT, CARROT, POTATO, BEETROOT -> amount;
            case BREAD -> amount * 3;
            default -> 0;
        };
        if (food > 0) {
            village.addFoodStock(food);
        }
    }

    private static Optional<Item> nearestItem(Villager villager, double radius, Predicate<Material> accepted) {
        double radiusSquared = radius * radius;
        return villager.getNearbyEntities(radius, radius, radius).stream()
                .filter(entity -> entity instanceof Item)
                .map(entity -> (Item) entity)
                .filter(item -> accepted == null || accepted.test(item.getItemStack().getType()))
                .filter(item -> item.getLocation().distanceSquared(villager.getLocation()) <= radiusSquared)
                .min(Comparator.comparingDouble(item -> item.getLocation().distanceSquared(villager.getLocation())));
    }

    private static Optional<Block> nearestMatureCrop(Location origin, int radius) {
        if (origin == null || origin.getWorld() == null) {
            return Optional.empty();
        }
        World world = origin.getWorld();
        Block best = null;
        double bestDistance = Double.MAX_VALUE;
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        for (int x = baseX - radius; x <= baseX + radius; x++) {
            for (int y = Math.max(world.getMinHeight(), baseY - 3); y <= Math.min(world.getMaxHeight() - 1, baseY + 3); y++) {
                for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!isCrop(block.getType()) || !(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
                        continue;
                    }
                    double distance = block.getLocation().distanceSquared(origin);
                    if (distance < bestDistance) {
                        best = block;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<Container> nearestContainer(Location origin, int radius) {
        if (origin == null || origin.getWorld() == null) {
            return Optional.empty();
        }
        World world = origin.getWorld();
        Container best = null;
        double bestDistance = Double.MAX_VALUE;
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        for (int x = baseX - radius; x <= baseX + radius; x++) {
            for (int y = Math.max(world.getMinHeight(), baseY - 4); y <= Math.min(world.getMaxHeight() - 1, baseY + 4); y++) {
                for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                    if (!(world.getBlockAt(x, y, z).getState() instanceof Container container)) {
                        continue;
                    }
                    double distance = container.getLocation().distanceSquared(origin);
                    if (distance < bestDistance) {
                        best = container;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static int moveInventory(Inventory source, Inventory target, Predicate<Material> acceptedFilter) {
        int moved = 0;
        for (int slot = 0; slot < source.getSize(); slot++) {
            ItemStack item = source.getItem(slot);
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            if (acceptedFilter != null && !acceptedFilter.test(item.getType())) {
                continue;
            }
            int requested = item.getAmount();
            Map<Integer, ItemStack> leftovers = target.addItem(item.clone());
            int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            int accepted = requested - leftoverAmount;
            if (accepted <= 0) {
                continue;
            }
            moved += accepted;
            if (leftoverAmount <= 0) {
                source.setItem(slot, null);
            } else {
                item.setAmount(leftoverAmount);
                source.setItem(slot, item);
            }
        }
        return moved;
    }

    private static int countMaterial(Inventory inventory, Material material) {
        int total = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private static int consumeFromInventory(Inventory inventory, Material material, int requested) {
        if (requested <= 0) {
            return 0;
        }
        int consumed = 0;
        for (int slot = 0; slot < inventory.getSize() && consumed < requested; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != material) {
                continue;
            }
            int taken = Math.min(requested - consumed, item.getAmount());
            if (item.getAmount() == taken) {
                inventory.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - taken);
                inventory.setItem(slot, item);
            }
            consumed += taken;
        }
        return consumed;
    }

    private static boolean isCrop(Material material) {
        return material == Material.WHEAT
                || material == Material.CARROTS
                || material == Material.POTATOES
                || material == Material.BEETROOTS;
    }

    private static Material cropDrop(Material crop) {
        return switch (crop) {
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            default -> Material.WHEAT;
        };
    }

    private static int cropYield(Material crop) {
        return switch (crop) {
            case WHEAT, BEETROOTS -> 1;
            default -> 2;
        };
    }

    private static void replant(Block crop) {
        Material type = crop.getType();
        crop.setType(type, false);
        if (crop.getBlockData() instanceof Ageable ageable) {
            ageable.setAge(0);
            crop.setBlockData(ageable, false);
        }
    }
}
