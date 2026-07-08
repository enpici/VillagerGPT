package io.github.enpici.villager.life.village;

import io.github.enpici.villager.life.agent.Agent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Container;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PhysicalResourceScanner {

    public PhysicalResourceSnapshot scan(VillageAI village, Collection<Agent> agents, int radius) {
        if (village == null || village.center().getWorld() == null) {
            return new PhysicalResourceSnapshot(Map.of(), 0, 0, 0);
        }

        Map<Material, Integer> materials = new EnumMap<>(Material.class);
        int agentInventories = 0;
        if (agents != null) {
            for (Agent agent : agents) {
                Villager villager = resolveValidVillager(agent);
                if (villager == null) {
                    continue;
                }
                mergeInventory(materials, villager.getInventory());
                agentInventories++;
            }
        }

        int containers = 0;
        for (Inventory inventory : collectContainerInventories(village.center(), radius)) {
            mergeInventory(materials, inventory);
            containers++;
        }

        return new PhysicalResourceSnapshot(Map.copyOf(materials), foodPoints(materials), containers, agentInventories);
    }

    public boolean consumeMaterial(VillageAI village, Collection<Agent> agents, Agent preferredAgent, Material material, int amount, int radius) {
        if (material == null || amount <= 0) {
            return false;
        }
        if (village == null || village.center().getWorld() == null) {
            return village != null && village.consumeMaterial(material, amount);
        }

        List<Inventory> sources = collectInventories(village, agents, preferredAgent, radius);
        int available = sources.stream().mapToInt(inventory -> countMaterial(inventory, material)).sum();
        if (available < amount) {
            return false;
        }

        int remaining = amount;
        for (Inventory source : sources) {
            remaining -= consumeFromInventory(source, material, remaining);
            if (remaining <= 0) {
                break;
            }
        }
        if (remaining > 0) {
            return false;
        }
        village.removeMaterialStock(material, amount);
        return true;
    }

    public int consumeFood(VillageAI village, Collection<Agent> agents, Agent preferredAgent, int requiredFoodPoints, int radius) {
        if (requiredFoodPoints <= 0) {
            return 0;
        }
        if (village == null || village.center().getWorld() == null) {
            return village != null && village.consumeFood(requiredFoodPoints) ? requiredFoodPoints : 0;
        }

        List<Inventory> sources = collectInventories(village, agents, preferredAgent, radius);
        int availableFood = sources.stream().mapToInt(PhysicalResourceScanner::foodPoints).sum();
        if (availableFood < requiredFoodPoints) {
            return 0;
        }

        int consumedFood = 0;
        for (Inventory source : sources) {
            consumedFood += consumeFoodFromInventory(source, requiredFoodPoints - consumedFood);
            if (consumedFood >= requiredFoodPoints) {
                break;
            }
        }
        if (consumedFood > 0) {
            village.addFoodStock(-consumedFood);
        }
        return consumedFood;
    }

    public static int foodPoints(Map<Material, Integer> materials) {
        if (materials == null || materials.isEmpty()) {
            return 0;
        }
        int food = 0;
        for (Map.Entry<Material, Integer> entry : materials.entrySet()) {
            food += nutrition(entry.getKey()) * Math.max(0, entry.getValue());
        }
        return food;
    }

    public static int nutrition(Material material) {
        return switch (material) {
            case WHEAT, CARROT, POTATO, BEETROOT -> 1;
            case BREAD -> 3;
            default -> 0;
        };
    }

    private List<Inventory> collectInventories(VillageAI village, Collection<Agent> agents, Agent preferredAgent, int radius) {
        List<Inventory> inventories = new ArrayList<>();
        Set<java.util.UUID> seenAgents = new HashSet<>();

        Villager preferred = resolveValidVillager(preferredAgent);
        if (preferred != null && seenAgents.add(preferred.getUniqueId())) {
            inventories.add(preferred.getInventory());
        }

        if (agents != null) {
            for (Agent agent : agents) {
                Villager villager = resolveValidVillager(agent);
                if (villager != null && seenAgents.add(villager.getUniqueId())) {
                    inventories.add(villager.getInventory());
                }
            }
        }

        inventories.addAll(collectContainerInventories(village.center(), radius));
        return inventories;
    }

    private static List<Inventory> collectContainerInventories(Location center, int radius) {
        if (center == null || center.getWorld() == null) {
            return List.of();
        }
        World world = center.getWorld();
        int scanRadius = Math.max(1, radius);
        int baseX = center.getBlockX();
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ();
        List<Inventory> inventories = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int x = baseX - scanRadius; x <= baseX + scanRadius; x++) {
            for (int y = Math.max(world.getMinHeight(), baseY - 6); y <= Math.min(world.getMaxHeight() - 1, baseY + 6); y++) {
                for (int z = baseZ - scanRadius; z <= baseZ + scanRadius; z++) {
                    if (!(world.getBlockAt(x, y, z).getState() instanceof Container container)) {
                        continue;
                    }
                    String key = world.getName() + ":" + x + ":" + y + ":" + z;
                    if (seen.add(key)) {
                        inventories.add(container.getInventory());
                    }
                }
            }
        }
        return inventories;
    }

    private static Villager resolveValidVillager(Agent agent) {
        if (agent == null) {
            return null;
        }
        try {
            Villager villager = agent.resolveVillager();
            return villager != null && villager.isValid() ? villager : null;
        } catch (IllegalStateException | NullPointerException exception) {
            agent.setMissingEntity(true);
            return null;
        }
    }

    private static void mergeInventory(Map<Material, Integer> materials, Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            materials.merge(item.getType(), item.getAmount(), Integer::sum);
        }
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

    private static int foodPoints(Inventory inventory) {
        int total = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                total += nutrition(item.getType()) * item.getAmount();
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

    private static int consumeFoodFromInventory(Inventory inventory, int requestedFoodPoints) {
        if (requestedFoodPoints <= 0) {
            return 0;
        }
        int consumedFood = 0;
        for (int slot = 0; slot < inventory.getSize() && consumedFood < requestedFoodPoints; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            int nutrition = nutrition(item.getType());
            if (nutrition <= 0) {
                continue;
            }
            item.setAmount(item.getAmount() - 1);
            consumedFood += nutrition;
            if (item.getAmount() <= 0) {
                inventory.setItem(slot, null);
            } else {
                inventory.setItem(slot, item);
            }
        }
        return consumedFood;
    }

    public record PhysicalResourceSnapshot(Map<Material, Integer> materials, int foodPoints, int containerCount, int agentInventoryCount) {
    }
}
