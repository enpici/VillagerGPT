package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.minecraft.MinecraftKnowledge;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Material;

import java.util.Map;

public class CraftSuppliesTask extends BaseTask {

    private static final int STORAGE_RADIUS = 24;

    private final MinecraftKnowledge knowledge = new MinecraftKnowledge();
    private boolean fulfilledAvailableRequest;

    public CraftSuppliesTask() {
        super("craft_supplies", 120L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return agent.isAdult();
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        fulfilledAvailableRequest = false;
        VillageAI.MaterialRequest request = nextCraftingRequest(agent, villageAI);
        if (request != null) {
            TaskStatus status = handleRequest(agent, villageAI, request);
            if (status == TaskStatus.SUCCESS) {
                agent.adjustNeed(NeedType.ENERGY, 2);
            }
            return status;
        }
        if (fulfilledAvailableRequest) {
            agent.adjustNeed(NeedType.ENERGY, 1);
            return TaskStatus.SUCCESS;
        }

        if (WorldItemInteraction.hasCarriedItems(agent)) {
            TaskStatus deposit = WorldItemInteraction.depositInventoryToNearestContainer(agent, villageAI, 24);
            if (deposit == TaskStatus.SUCCESS || deposit == TaskStatus.RUNNING) {
                return deposit;
            }
        }

        if (craft(agent, villageAI, Material.OAK_PLANKS, 4)) {
            return crafted(agent);
        }
        if (craft(agent, villageAI, Material.STICK, 4)) {
            return crafted(agent);
        }
        if (WorldItemInteraction.availableItemCount(agent, villageAI, Material.COBBLESTONE, STORAGE_RADIUS) >= 8
                && WorldItemInteraction.availableItemCount(agent, villageAI, Material.FURNACE, STORAGE_RADIUS) == 0
                && craft(agent, villageAI, Material.FURNACE, 1)) {
            return crafted(agent);
        }
        if (canSmelt(agent, villageAI, Material.COBBLESTONE) && smelt(agent, villageAI, Material.COBBLESTONE, 1)) {
            return crafted(agent);
        }
        if (WorldItemInteraction.availableItemCount(agent, villageAI, Material.STONE, STORAGE_RADIUS) >= 4
                && WorldItemInteraction.consumeItems(agent, villageAI, Material.STONE, 4, STORAGE_RADIUS)) {
            WorldItemInteraction.addGatheredItem(agent, villageAI, Material.STONE_BRICKS, 4);
            return crafted(agent);
        }
        if (WorldItemInteraction.availableItemCount(agent, villageAI, Material.COAL, STORAGE_RADIUS) > 0
                && WorldItemInteraction.availableItemCount(agent, villageAI, Material.STICK, STORAGE_RADIUS) > 0
                && WorldItemInteraction.consumeItems(agent, villageAI, Material.COAL, 1, STORAGE_RADIUS)
                && WorldItemInteraction.consumeItems(agent, villageAI, Material.STICK, 1, STORAGE_RADIUS)) {
            WorldItemInteraction.addGatheredItem(agent, villageAI, Material.TORCH, 4);
            return crafted(agent);
        }

        villageAI.enqueueMaterialRequests(java.util.Map.of(Material.OAK_LOG, 1, Material.COBBLESTONE, 3));
        agent.setLastEvent("role:requested_crafting_materials");
        return TaskStatus.RETRYABLE_FAILED;
    }

    private VillageAI.MaterialRequest nextCraftingRequest(Agent agent, VillageAI villageAI) {
        java.util.List<VillageAI.MaterialRequest> skipped = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            VillageAI.MaterialRequest request = villageAI.pollMaterialRequest();
            if (request == null) {
                break;
            }
            if (isAlreadyAvailable(agent, villageAI, request)) {
                fulfilledAvailableRequest = true;
                agent.setLastEvent("role:material_available:" + request.material().name().toLowerCase());
                return null;
            }
            if (isCraftingRequest(request.material())) {
                for (VillageAI.MaterialRequest skippedRequest : skipped) {
                    villageAI.requeueMaterialRequest(skippedRequest);
                }
                return request;
            }
            skipped.add(request);
        }
        for (VillageAI.MaterialRequest request : skipped) {
            villageAI.requeueMaterialRequest(request);
        }
        if (!skipped.isEmpty()) {
            agent.setLastEvent("role:skipped_non_crafting_request:" + skipped.get(0).material().name().toLowerCase());
        }
        return null;
    }

    private boolean isAlreadyAvailable(Agent agent, VillageAI villageAI, VillageAI.MaterialRequest request) {
        return request != null
                && request.material() != null
                && WorldItemInteraction.availableItemCount(agent, villageAI, request.material(), STORAGE_RADIUS) >= request.amount();
    }

    private boolean isCraftingRequest(Material material) {
        return material != null
                && (!knowledge.craftingInputs(material).isEmpty()
                || material == Material.STONE
                || knowledge.smeltingOutput(material) != null);
    }

    private TaskStatus handleRequest(Agent agent, VillageAI villageAI, VillageAI.MaterialRequest request) {
        Material requested = request.material();
        int completed = 0;

        if (requested == Material.STONE && canSmelt(agent, villageAI, Material.COBBLESTONE)) {
            completed = smeltBatch(agent, villageAI, Material.COBBLESTONE, request.amount());
            agent.setLastEvent("role:smelted:cobblestone_to_stone");
        } else if (craft(agent, villageAI, requested, 1)) {
            completed = 1;
            agent.setLastEvent("role:crafted:" + requested.name().toLowerCase());
        } else if (knowledge.smeltingOutput(requested) != null) {
            completed = smeltBatch(agent, villageAI, requested, request.amount());
            agent.setLastEvent("role:smelted:" + requested.name().toLowerCase());
        }

        if (completed > 0) {
            int remaining = request.amount() - completed;
            if (remaining > 0) {
                villageAI.requeueMaterialRequest(new VillageAI.MaterialRequest(requested, remaining));
            }
            return TaskStatus.SUCCESS;
        }

        requestInputsFor(agent, villageAI, requested);
        villageAI.requeueMaterialRequest(request);
        return TaskStatus.RETRYABLE_FAILED;
    }

    private boolean craft(Agent agent, VillageAI villageAI, Material output, int outputAmount) {
        Map<Material, Integer> inputs = knowledge.craftingInputs(output);
        if (inputs.isEmpty() || inputs.entrySet().stream()
                .anyMatch(entry -> WorldItemInteraction.availableItemCount(agent, villageAI, entry.getKey(), STORAGE_RADIUS) < entry.getValue())) {
            return false;
        }
        for (Map.Entry<Material, Integer> input : inputs.entrySet()) {
            if (!WorldItemInteraction.consumeItems(agent, villageAI, input.getKey(), input.getValue(), STORAGE_RADIUS)) {
                agent.setLastEvent("role:missing_physical_input:" + input.getKey().name().toLowerCase());
                return false;
            }
        }
        WorldItemInteraction.addGatheredItem(agent, villageAI, output, outputAmount);
        return true;
    }

    private boolean canSmelt(Agent agent, VillageAI villageAI, Material input) {
        return knowledge.smeltingOutput(input) != null
                && WorldItemInteraction.availableItemCount(agent, villageAI, input, STORAGE_RADIUS) > 0
                && WorldItemInteraction.availableItemCount(agent, villageAI, Material.FURNACE, STORAGE_RADIUS) > 0
                && WorldItemInteraction.availableItemCount(agent, villageAI, Material.COAL, STORAGE_RADIUS) > 0;
    }

    private boolean smelt(Agent agent, VillageAI villageAI, Material input, int amount) {
        return smeltBatch(agent, villageAI, input, amount) > 0;
    }

    private int smeltBatch(Agent agent, VillageAI villageAI, Material input, int amount) {
        Material output = knowledge.smeltingOutput(input);
        if (output == null || amount <= 0) {
            return 0;
        }
        int completed = 0;
        while (completed < amount && canSmelt(agent, villageAI, input)) {
            if (!WorldItemInteraction.consumeItems(agent, villageAI, input, 1, STORAGE_RADIUS)
                    || !WorldItemInteraction.consumeItems(agent, villageAI, Material.COAL, 1, STORAGE_RADIUS)) {
                break;
            }
            WorldItemInteraction.addGatheredItem(agent, villageAI, output, 1);
            completed++;
        }
        return completed;
    }

    private void requestInputsFor(Agent agent, VillageAI villageAI, Material requested) {
        Map<Material, Integer> inputs = knowledge.craftingInputs(requested);
        if (!inputs.isEmpty()) {
            villageAI.enqueueMaterialRequests(inputs);
            agent.setLastEvent("role:requested_recipe_inputs:" + requested.name().toLowerCase());
            return;
        }
        if (requested == Material.STONE) {
            villageAI.enqueueMaterialRequests(Map.of(Material.COBBLESTONE, 1, Material.FURNACE, 1, Material.COAL, 1));
            agent.setLastEvent("role:requested_smelting_inputs:stone");
            return;
        }
        agent.setLastEvent("role:cannot_craft:" + requested.name().toLowerCase());
    }

    private TaskStatus crafted(Agent agent) {
        agent.adjustNeed(NeedType.ENERGY, 2);
        agent.setLastEvent("role:crafted_supplies");
        return TaskStatus.SUCCESS;
    }
}
