package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.AgentGoal;
import io.github.enpici.villager.life.minecraft.MinecraftKnowledge;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Material;

import java.util.Map;

public class AgentToolExecutor {

    private final MinecraftKnowledge knowledge = new MinecraftKnowledge();

    public ToolExecution apply(VillageAI village, AgentToolRequest request) {
        if (village == null || request == null || request.tool() == null) {
            return ToolExecution.skipped("missing_tool");
        }
        int amount = clampAmount(request.amount());
        return switch (request.tool()) {
            case CRAFT_ITEM -> requestMaterial(village, request.material(), amount, AgentGoal.CRAFT_SUPPLIES, "craft_request");
            case MINE_BLOCK, BREAK_BLOCK -> requestMaterial(village, desiredMiningMaterial(request.material()), amount, AgentGoal.GATHER_MATERIALS, "mine_request");
            case SMELT_ITEM -> requestSmelting(village, request.material(), amount);
            case GET_FOOD -> ToolExecution.accepted(AgentGoal.WORK_FOOD, "food_action");
            case EAT_FOOD -> ToolExecution.accepted(AgentGoal.SURVIVE_HUNGER, "eat_action");
            case SLEEP_OR_WAIT_SAFE -> ToolExecution.accepted(AgentGoal.REST, "sleep_or_wait_action");
            case BUILD_SHELTER -> ToolExecution.accepted(AgentGoal.BUILD_SHELTER, "shelter_action");
            case FLEE_FROM_MOB, AVOID_HAZARD -> ToolExecution.accepted(AgentGoal.SEEK_SAFETY, "safety_action");
            case ATTACK_MOB -> ToolExecution.accepted(AgentGoal.PATROL, "combat_action");
            case PLACE_BLOCK -> ToolExecution.accepted(AgentGoal.BUILD_HOUSING, "place_block_action");
            case DEPOSIT_TO_CHEST, WITHDRAW_FROM_CHEST, PICKUP_ITEMS -> ToolExecution.accepted(AgentGoal.GATHER_MATERIALS, "inventory_action");
            case EQUIP_ITEM -> request.material() != null && isCraftableTool(request.material())
                    ? requestMaterial(village, request.material(), 1, AgentGoal.CRAFT_SUPPLIES, "equip_needs_tool")
                    : ToolExecution.accepted(AgentGoal.GATHER_MATERIALS, "equip_action");
            case LOOK_AROUND, MOVE_TO, REMEMBER_FACT, ASK_AGENT -> ToolExecution.accepted(AgentGoal.IDLE, "non_material_action");
        };
    }

    public AgentGoal goalFor(AgentToolRequest request) {
        if (request == null || request.tool() == null) {
            return null;
        }
        return switch (request.tool()) {
            case CRAFT_ITEM, SMELT_ITEM, EQUIP_ITEM -> AgentGoal.CRAFT_SUPPLIES;
            case MINE_BLOCK, BREAK_BLOCK, PICKUP_ITEMS, DEPOSIT_TO_CHEST, WITHDRAW_FROM_CHEST -> AgentGoal.GATHER_MATERIALS;
            case GET_FOOD -> AgentGoal.WORK_FOOD;
            case EAT_FOOD -> AgentGoal.SURVIVE_HUNGER;
            case SLEEP_OR_WAIT_SAFE -> AgentGoal.REST;
            case BUILD_SHELTER -> AgentGoal.BUILD_SHELTER;
            case FLEE_FROM_MOB, AVOID_HAZARD -> AgentGoal.SEEK_SAFETY;
            case ATTACK_MOB -> AgentGoal.PATROL;
            case PLACE_BLOCK -> AgentGoal.BUILD_HOUSING;
            case LOOK_AROUND, MOVE_TO, REMEMBER_FACT, ASK_AGENT -> AgentGoal.IDLE;
        };
    }

    private ToolExecution requestSmelting(VillageAI village, Material input, int amount) {
        if (input == null) {
            return ToolExecution.rejected("smelt_missing_input");
        }
        Material output = knowledge.smeltingOutput(input);
        if (output == null) {
            return ToolExecution.rejected("not_smeltable:" + input.name());
        }
        village.enqueuePriorityMaterialRequest(output, amount);
        return ToolExecution.accepted(AgentGoal.CRAFT_SUPPLIES, "smelt_request:" + input.name() + "->" + output.name());
    }

    private ToolExecution requestMaterial(VillageAI village, Material material, int amount, AgentGoal goal, String detail) {
        if (material == null) {
            return ToolExecution.rejected(detail + ":missing_material");
        }
        village.enqueuePriorityMaterialRequest(material, amount);
        return ToolExecution.accepted(goal, detail + ":" + material.name() + "x" + amount);
    }

    private Material desiredMiningMaterial(Material requested) {
        if (requested == null) {
            return null;
        }
        Material drop = knowledge.droppedItem(requested, knowledge.preferredToolFor(requested), false);
        if (drop != null && drop != requested) {
            return drop;
        }
        return requested;
    }

    private boolean isCraftableTool(Material material) {
        return material != null && !knowledge.craftingInputs(material).isEmpty() && material.name().endsWith("_AXE")
                || material != null && material.name().endsWith("_PICKAXE")
                || material != null && material.name().endsWith("_SHOVEL")
                || material != null && material.name().endsWith("_HOE")
                || material != null && material.name().endsWith("_SWORD");
    }

    private int clampAmount(Integer amount) {
        if (amount == null) {
            return 1;
        }
        return Math.max(1, Math.min(64, amount));
    }

    public record ToolExecution(boolean accepted, AgentGoal impliedGoal, String detail) {
        static ToolExecution accepted(AgentGoal impliedGoal, String detail) {
            return new ToolExecution(true, impliedGoal, detail);
        }

        static ToolExecution rejected(String detail) {
            return new ToolExecution(false, null, detail);
        }

        static ToolExecution skipped(String detail) {
            return new ToolExecution(false, null, detail);
        }
    }
}
