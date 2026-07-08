package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;

public class RoleAssignmentService {

    public AgentRole chooseRoleForAdult(Agent agent, VillageAI village) {
        if (village.foodStock() < Math.max(10, village.population() * 2)) {
            return AgentRole.FARMER;
        }
        if (needsMining(village)) {
            return AgentRole.MINER;
        }
        if (canCraftUsefulSupplies(village)) {
            return AgentRole.CRAFTER;
        }
        if (village.pendingBlueprintCount() > 0 || village.bedCount() < village.population()) {
            return AgentRole.BUILDER;
        }
        if (village.threatDetected()) {
            return AgentRole.GUARD;
        }

        Map<AgentRole, Integer> counts = roleCounts(village);
        if (counts.getOrDefault(AgentRole.FARMER, 0) == 0) return AgentRole.FARMER;
        if (counts.getOrDefault(AgentRole.BUILDER, 0) == 0) return AgentRole.BUILDER;
        if (village.population() >= 3 && counts.getOrDefault(AgentRole.MINER, 0) == 0) return AgentRole.MINER;
        if (village.population() >= 4 && counts.getOrDefault(AgentRole.CRAFTER, 0) == 0) return AgentRole.CRAFTER;
        if (counts.getOrDefault(AgentRole.GUARD, 0) == 0 && village.population() >= 4) return AgentRole.GUARD;
        if (village.population() >= 5 && counts.getOrDefault(AgentRole.TRADER, 0) == 0) return AgentRole.TRADER;
        if (village.population() >= 6 && counts.getOrDefault(AgentRole.LEADER, 0) == 0) return AgentRole.LEADER;

        return agent.role() != null ? agent.role() : AgentRole.FARMER;
    }

    public Map<AgentRole, Integer> roleCounts(VillageAI village) {
        EnumMap<AgentRole, Integer> counts = new EnumMap<>(AgentRole.class);
        for (AgentRole role : AgentRole.values()) {
            counts.put(role, 0);
        }
        for (Agent agent : village.agentManager().all()) {
            counts.merge(agent.role(), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    private boolean needsMining(VillageAI village) {
        return village.pendingMaterials().keySet().stream().anyMatch(this::isMineable)
                || village.availableMaterial(Material.COBBLESTONE) < 8 && village.population() >= 3;
    }

    private boolean canCraftUsefulSupplies(VillageAI village) {
        return village.availableMaterial(Material.OAK_LOG) > 0
                || village.availableMaterial(Material.COBBLESTONE) >= 3
                || village.availableMaterial(Material.COAL) > 0 && village.availableMaterial(Material.STICK) > 0;
    }

    private boolean isMineable(Material material) {
        return material == Material.COBBLESTONE
                || material == Material.STONE
                || material == Material.STONE_BRICKS
                || material == Material.COAL
                || material == Material.IRON_ORE
                || material == Material.RAW_IRON
                || material == Material.IRON_INGOT;
    }
}
