package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.VillagerLifePlugin;
import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.integration.CitizensGateway;
import io.github.enpici.villager.life.minecraft.MinecraftKnowledge;
import io.github.enpici.villager.life.minecraft.WorldBlockSearch;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Map;

public class MineResourcesTask extends BaseTask {

    private final MinecraftKnowledge knowledge = new MinecraftKnowledge();
    private final WorldBlockSearch blockSearch = new WorldBlockSearch();
    private static final int STORAGE_RADIUS = 24;
    private boolean fulfilledAvailableRequest;

    public MineResourcesTask() {
        super("mine_resources", 240L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return agent.isAdult();
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        fulfilledAvailableRequest = false;
        VillageAI.MaterialRequest request = nextGatherableRequest(agent, villageAI);
        if (fulfilledAvailableRequest) {
            agent.adjustNeed(NeedType.ENERGY, 1);
            return TaskStatus.SUCCESS;
        }
        if (request == null && WorldItemInteraction.hasCarriedItems(agent, material -> !WorldItemInteraction.isTool(material))) {
            TaskStatus deposit = WorldItemInteraction.depositInventoryToNearestContainer(agent, villageAI, 24, material -> !WorldItemInteraction.isTool(material));
            if (deposit == TaskStatus.SUCCESS || deposit == TaskStatus.RUNNING) {
                return deposit;
            }
        }

        Material desired = request != null ? request.material() : defaultDesired(agent, villageAI);
        int amount = request != null ? request.amount() : 5;

        Material sourceBlock = knowledge.sourceBlockFor(desired);
        Material requiredTool = knowledge.requiredToolFor(sourceBlock);
        Material preferredTool = knowledge.preferredToolFor(sourceBlock);
        Material usableTool = findUsableTool(agent, villageAI, requiredTool != null ? requiredTool : preferredTool);
        if (requiredTool != null && usableTool == null) {
            if (craftToolIfPossible(agent, villageAI, requiredTool)) {
                usableTool = requiredTool;
            } else if (canGatherToolInputsByHand(requiredTool)) {
                villageAI.requeueMaterialRequest(new VillageAI.MaterialRequest(requiredTool, 1));
                villageAI.enqueueMaterialRequests(knowledge.craftingInputs(requiredTool));
                if (request != null) {
                    villageAI.requeueMaterialRequest(request);
                }
                desired = Material.OAK_LOG;
                amount = Math.max(2, amount);
                sourceBlock = knowledge.sourceBlockFor(desired);
                requiredTool = null;
                usableTool = null;
                agent.setLastEvent("role:gathering_tool_inputs:" + desired.name().toLowerCase());
            } else {
                villageAI.requeueMaterialRequest(new VillageAI.MaterialRequest(requiredTool, 1));
                villageAI.enqueueMaterialRequests(knowledge.craftingInputs(requiredTool));
                if (request != null) {
                    villageAI.requeueMaterialRequest(request);
                }
                agent.setLastEvent("role:needs_tool:" + requiredTool.name().toLowerCase());
                return TaskStatus.RETRYABLE_FAILED;
            }
        } else if (requiredTool == null && preferredTool != null && usableTool == null && craftToolIfPossible(agent, villageAI, preferredTool)) {
            usableTool = preferredTool;
        }

        if (moveNpcToSource(agent, villageAI, sourceBlock, usableTool)) {
            return TaskStatus.RUNNING;
        }

        Material dropped = mineOne(agent, villageAI, sourceBlock, usableTool);
        if (dropped == null) {
            if (request != null) {
                villageAI.requeueMaterialRequest(request);
            }
            agent.setLastEvent("role:no_source_block:" + sourceBlock.name().toLowerCase());
            return TaskStatus.RETRYABLE_FAILED;
        }
        int gathered = gatheredAmount(amount, usableTool);
        if (dropped == desired) {
            WorldItemInteraction.addGatheredItem(agent, villageAI, dropped, gathered);
            completeRequest(villageAI, request, gathered);
        } else if (knowledge.needsSmelting(dropped, desired)) {
            WorldItemInteraction.addGatheredItem(agent, villageAI, dropped, gathered);
            villageAI.enqueueMaterialRequests(Map.of(desired, Math.max(1, amount)));
            completeRequest(villageAI, request, gathered);
            agent.setLastEvent("role:mined_intermediate:" + dropped.name().toLowerCase());
        } else {
            WorldItemInteraction.addGatheredItem(agent, villageAI, dropped, gathered);
            if (request != null) {
                villageAI.requeueMaterialRequest(request);
            }
        }

        agent.adjustNeed(NeedType.ENERGY, 5);
        agent.adjustNeed(NeedType.HUNGER, 3);
        if (!agent.lastEvent().startsWith("role:mined_intermediate")) {
            agent.setLastEvent("role:mined:" + sourceBlock.name().toLowerCase());
        }
        return TaskStatus.SUCCESS;
    }

    private VillageAI.MaterialRequest nextGatherableRequest(Agent agent, VillageAI villageAI) {
        java.util.List<VillageAI.MaterialRequest> skipped = new java.util.ArrayList<>();
        VillageAI.MaterialRequest selected = null;
        for (int i = 0; i < 8; i++) {
            VillageAI.MaterialRequest request = villageAI.pollMaterialRequest();
            if (request == null) {
                break;
            }
            if (request.material() != null
                    && WorldItemInteraction.availableItemCount(agent, villageAI, request.material(), STORAGE_RADIUS) >= request.amount()) {
                selected = request;
                fulfilledAvailableRequest = true;
                agent.setLastEvent("role:material_available:" + request.material().name().toLowerCase());
                break;
            }
            if (isGatherableByMining(request.material())) {
                selected = request;
                break;
            }
            skipped.add(request);
        }
        for (VillageAI.MaterialRequest request : skipped) {
            villageAI.requeueMaterialRequest(request);
        }
        if (selected == null && !skipped.isEmpty()) {
            agent.setLastEvent("role:skipped_non_mining_request:" + skipped.get(0).material().name().toLowerCase());
        }
        return selected;
    }

    private boolean isGatherableByMining(Material desired) {
        if (desired == null) {
            return false;
        }
        if (!knowledge.craftingInputs(desired).isEmpty()) {
            return false;
        }
        Material smeltingInput = knowledge.smeltingInputFor(desired);
        Material source = knowledge.sourceBlockFor(smeltingInput != null ? smeltingInput : desired);
        if (source == null || !isKnownGatherableBlock(source)) {
            return false;
        }
        return source != desired
                || knowledge.requiredToolFor(source) != null
                || knowledge.preferredToolFor(source) != null
                || source == Material.DIRT
                || source == Material.GRAVEL
                || source == Material.SAND;
    }

    private boolean isKnownGatherableBlock(Material source) {
        String name = source.name();
        return source == Material.STONE
                || source == Material.COBBLESTONE
                || source == Material.DIRT
                || source == Material.GRASS_BLOCK
                || source == Material.GRAVEL
                || source == Material.SAND
                || name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_ORE")
                || name.endsWith("_LEAVES");
    }

    private Material defaultDesired(Agent agent, VillageAI villageAI) {
        return findUsableTool(agent, villageAI, Material.WOODEN_PICKAXE) != null
                ? Material.COBBLESTONE
                : Material.OAK_LOG;
    }

    private int gatheredAmount(int amount, Material usableTool) {
        int limit = usableTool != null ? 5 : 2;
        return Math.max(1, Math.min(limit, amount));
    }

    private boolean canGatherToolInputsByHand(Material requiredTool) {
        return requiredTool == Material.WOODEN_PICKAXE || requiredTool == Material.STONE_PICKAXE;
    }

    private boolean craftToolIfPossible(Agent agent, VillageAI villageAI, Material tool) {
        Map<Material, Integer> inputs = knowledge.craftingInputs(tool);
        if (inputs.isEmpty()) {
            return false;
        }
        for (Map.Entry<Material, Integer> input : inputs.entrySet()) {
            if (WorldItemInteraction.availableItemCount(agent, villageAI, input.getKey(), STORAGE_RADIUS) < input.getValue()) {
                return false;
            }
        }
        for (Map.Entry<Material, Integer> input : inputs.entrySet()) {
            if (!WorldItemInteraction.consumeItems(agent, villageAI, input.getKey(), input.getValue(), STORAGE_RADIUS)) {
                agent.setLastEvent("role:missing_physical_input:" + input.getKey().name().toLowerCase());
                return false;
            }
        }
        WorldItemInteraction.addGatheredItem(agent, villageAI, tool, 1);
        agent.setLastEvent("role:crafted_tool:" + tool.name().toLowerCase());
        return true;
    }

    private Material mineOne(Agent agent, VillageAI villageAI, Material sourceBlock, Material usableTool) {
        var villager = resolveVillager(agent);
        var origin = npcLocation(agent).orElse(villager != null ? villager.getLocation() : villageAI.center());
        Block block = blockSearch.nearestBlock(origin, sourceBlock, 24).orElse(null);
        if (block != null) {
            Material blockType = block.getType();
            playSwing(agent);
            block.setType(Material.AIR, false);
            damageTool(agent, villageAI, usableTool);
            return knowledge.droppedItem(blockType, usableTool, false);
        }
        if (origin.getWorld() != null) {
            return null;
        }
        playSwing(agent);
        damageTool(agent, villageAI, usableTool);
        return knowledge.droppedItem(sourceBlock, usableTool, false);
    }

    private boolean moveNpcToSource(Agent agent, VillageAI villageAI, Material sourceBlock, Material usableTool) {
        VillagerLifePlugin plugin = VillagerLifePlugin.instance();
        CitizensGateway citizens = plugin != null && plugin.isCitizensIntegrationEnabled() ? plugin.citizensAdapter() : null;
        var villager = resolveVillager(agent);
        if (villager == null) {
            return false;
        }
        equipMainHand(agent, usableTool);
        var origin = citizens != null && citizens.currentLocation() != null ? citizens.currentLocation() : villager.getLocation();
        Block target = blockSearch.nearestBlock(origin, sourceBlock, 24).orElse(null);
        if (target == null) {
            return false;
        }
        var targetLocation = interactionLocation(target);
        var current = citizens != null && citizens.currentLocation() != null ? citizens.currentLocation() : villager.getLocation();
        if (current != null && current.getWorld() == targetLocation.getWorld()
                && current.distanceSquared(targetLocation) <= 6.25) {
            return false;
        }
        if (citizens != null && citizens.getOrCreateNpc(villager) != null) {
            citizens.navigateTo(targetLocation);
        } else {
            TaskMovement.moveTo(villager, targetLocation, 0.9);
        }
        agent.setLastEvent("role:moving_to_block:" + sourceBlock.name().toLowerCase());
        return true;
    }

    private org.bukkit.Location interactionLocation(Block block) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP}) {
            Block adjacent = block.getRelative(face);
            if (adjacent.getType().isAir()) {
                return adjacent.getLocation().add(0.5, 0.0, 0.5);
            }
        }
        return block.getLocation().add(0.5, 1.0, 0.5);
    }

    private java.util.Optional<org.bukkit.Location> npcLocation(Agent agent) {
        VillagerLifePlugin plugin = VillagerLifePlugin.instance();
        CitizensGateway citizens = plugin != null && plugin.isCitizensIntegrationEnabled() ? plugin.citizensAdapter() : null;
        if (citizens == null) {
            return java.util.Optional.empty();
        }
        var villager = resolveVillager(agent);
        if (villager == null || citizens.getOrCreateNpc(villager) == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(citizens.currentLocation());
    }

    private void playSwing(Agent agent) {
        VillagerLifePlugin plugin = VillagerLifePlugin.instance();
        CitizensGateway citizens = plugin != null && plugin.isCitizensIntegrationEnabled() ? plugin.citizensAdapter() : null;
        if (citizens == null) {
            var villager = resolveVillager(agent);
            if (villager != null) {
                villager.swingMainHand();
            }
            return;
        }
        var villager = resolveVillager(agent);
        if (villager != null && citizens.getOrCreateNpc(villager) != null) {
            citizens.playSwingAnimation();
        }
    }

    private void damageTool(Agent agent, VillageAI villageAI, Material usableTool) {
        if (usableTool == null) {
            return;
        }
        VillagerLifePlugin plugin = VillagerLifePlugin.instance();
        CitizensGateway citizens = plugin != null && plugin.isCitizensIntegrationEnabled() ? plugin.citizensAdapter() : null;
        var villager = resolveVillager(agent);
        if (villager == null) {
            return;
        }
        if (citizens != null && citizens.getOrCreateNpc(villager) != null && citizens.damageMainHand(usableTool, 1)) {
            villageAI.consumeMaterial(usableTool, 1);
            agent.setLastEvent("role:broke_tool:" + usableTool.name().toLowerCase());
            return;
        }
        ItemStack item = villager.getEquipment() != null ? villager.getEquipment().getItemInMainHand() : null;
        if (item == null || item.getType() != usableTool || item.getType().getMaxDurability() <= 0) {
            return;
        }
        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            return;
        }
        int nextDamage = damageable.getDamage() + 1;
        if (nextDamage >= item.getType().getMaxDurability()) {
            villager.getEquipment().setItemInMainHand(null);
            villageAI.consumeMaterial(usableTool, 1);
            agent.setLastEvent("role:broke_tool:" + usableTool.name().toLowerCase());
            return;
        }
        damageable.setDamage(nextDamage);
        item.setItemMeta(damageable);
        villager.getEquipment().setItemInMainHand(item);
    }

    private org.bukkit.entity.Villager resolveVillager(Agent agent) {
        try {
            return agent.resolveVillager();
        } catch (IllegalStateException | NullPointerException exception) {
            return null;
        }
    }

    private Material findUsableTool(Agent agent, VillageAI villageAI, Material requiredTool) {
        if (requiredTool == null) {
            return null;
        }
        var villager = resolveVillager(agent);
        if (villager != null) {
            for (ItemStack item : villager.getInventory().getContents()) {
                if (item != null && item.getAmount() > 0 && knowledge.isAcceptableTool(requiredTool, item.getType())) {
                    return item.getType();
                }
            }
            ItemStack hand = villager.getEquipment() != null ? villager.getEquipment().getItemInMainHand() : null;
            if (hand != null && knowledge.isAcceptableTool(requiredTool, hand.getType())) {
                return hand.getType();
            }
        }
        return knowledge.findUsableTool(villageAI.materialStockSnapshot(), requiredTool);
    }

    private void equipMainHand(Agent agent, Material material) {
        if (material == null) {
            return;
        }
        VillagerLifePlugin plugin = VillagerLifePlugin.instance();
        CitizensGateway citizens = plugin != null && plugin.isCitizensIntegrationEnabled() ? plugin.citizensAdapter() : null;
        var villager = resolveVillager(agent);
        if (villager == null) {
            return;
        }
        if (citizens != null && citizens.getOrCreateNpc(villager) != null) {
            citizens.equipMainHand(material);
            return;
        }
        if (villager.getEquipment() != null) {
            ItemStack current = villager.getEquipment().getItemInMainHand();
            if (current == null || current.getType() != material) {
                villager.getEquipment().setItemInMainHand(new ItemStack(material));
            }
        }
    }

    private void completeRequest(VillageAI villageAI, VillageAI.MaterialRequest request, int completed) {
        if (request == null) {
            return;
        }
        int remaining = request.amount() - completed;
        if (remaining > 0) {
            villageAI.requeueMaterialRequest(new VillageAI.MaterialRequest(request.material(), remaining));
        }
    }
}
