package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentGoal;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Bukkit;
import org.bukkit.Material;

public class AgentPlanner {

    private static final long REPRODUCTION_COOLDOWN_TICKS = 1_200L;

    public AgentGoal plan(Agent agent, VillageAI village) {
        return plan(agent, village, currentTick());
    }

    public AgentGoal plan(Agent agent, VillageAI village, long currentTick) {
        Candidate best = chooseGoal(agent, village, currentTick);
        agent.assignGoal(best.goal(), best.priority(), best.reason(), currentTick);
        return best.goal();
    }

    private Candidate chooseGoal(Agent agent, VillageAI village, long currentTick) {
        Candidate best = new Candidate(AgentGoal.IDLE, 5, "no urgent village or personal need");

        if ((village.threatDetected() || shouldHaveNightShelter(village))
                && (agent.role() == AgentRole.BUILDER || agent.role() == AgentRole.LEADER)
                && village.pendingBlueprintsSnapshot(4).stream().anyMatch(id -> id.startsWith("ai_shelter"))) {
            best = max(best, new Candidate(AgentGoal.BUILD_SHELTER, 100, "emergency shelter is planned for monster safety"));
        }
        if (village.threatDetected() || agent.needLevel(NeedType.SAFETY) >= 70) {
            best = max(best, new Candidate(AgentGoal.SEEK_SAFETY, 100, "threat detected near village"));
        }
        if (agent.needLevel(NeedType.HUNGER) >= 70) {
            best = max(best, new Candidate(AgentGoal.SURVIVE_HUNGER, 95, "hunger is critical"));
        }
        if (agent.needLevel(NeedType.ENERGY) >= 75) {
            best = max(best, new Candidate(AgentGoal.REST, 88, "energy is low"));
        }
        if (!agent.isAdult()) {
            int priority = 35 + (int) Math.round(agent.needLevel(NeedType.SOCIAL) * 0.3);
            best = max(best, new Candidate(AgentGoal.GROW_UP, priority, "child learning from the village"));
            return best;
        }

        if (hasChildNeedingCare(agent, village)) {
            best = max(best, new Candidate(AgentGoal.RAISE_CHILD, 72, "child needs food or attention"));
        }
        if (canSeekPartner(agent, village, currentTick)) {
            int priority = 46 + (int) Math.round(agent.needLevel(NeedType.SOCIAL) * 0.2);
            best = max(best, new Candidate(AgentGoal.SEEK_PARTNER, priority, "village can grow and agent is ready for family"));
        }

        best = max(best, roleGoal(agent, village));

        if (agent.needLevel(NeedType.SOCIAL) >= 60) {
            best = max(best, new Candidate(AgentGoal.SOCIALIZE, 58, "social need is high"));
        }

        return best;
    }

    private Candidate roleGoal(Agent agent, VillageAI village) {
        AgentRole role = agent.role();
        int dayBonus = isDay(village) ? 8 : 0;
        return switch (role) {
            case FARMER -> village.foodStock() < Math.max(12, village.population() * 3)
                    ? new Candidate(AgentGoal.WORK_FOOD, 74 + dayBonus, "food stock is below village buffer")
                    : new Candidate(AgentGoal.WORK_FOOD, 42 + dayBonus, "maintaining food reserves");
            case BUILDER -> village.bedCount() < village.population() || village.pendingBlueprintCount() > 0
                    ? new Candidate(AgentGoal.BUILD_HOUSING, 78 + dayBonus, "housing or construction is pending")
                    : new Candidate(AgentGoal.BUILD_HOUSING, 35 + dayBonus, "checking village structures");
            case MINER -> !village.pendingMaterials().isEmpty()
                    ? new Candidate(AgentGoal.GATHER_MATERIALS, 76 + dayBonus, "materials are missing for construction")
                    : new Candidate(AgentGoal.GATHER_MATERIALS, 40 + dayBonus, "stockpiling useful materials");
            case CRAFTER -> village.availableMaterial(Material.OAK_LOG) > 0
                    || village.availableMaterial(Material.COBBLESTONE) >= 3
                    || !village.pendingMaterials().isEmpty()
                    ? new Candidate(AgentGoal.CRAFT_SUPPLIES, 70 + dayBonus, "raw materials can become useful supplies")
                    : new Candidate(AgentGoal.CRAFT_SUPPLIES, 38 + dayBonus, "waiting for raw materials to craft");
            case GUARD -> village.threatDetected()
                    ? new Candidate(AgentGoal.SEEK_SAFETY, 98, "guard responding to threat")
                    : new Candidate(AgentGoal.PATROL, 55 + dayBonus, "guard patrol duty");
            case TRADER -> new Candidate(AgentGoal.TRADE_GOODS, 55 + dayBonus, "trader looking for exchanges");
            case LEADER -> village.bedCount() < village.population() || village.foodStock() < village.population() * 2
                    ? new Candidate(AgentGoal.MANAGE_VILLAGE, 72 + dayBonus, "leader prioritizing village stability")
                    : new Candidate(AgentGoal.MANAGE_VILLAGE, 52 + dayBonus, "leader inspecting village priorities");
        };
    }

    private boolean hasChildNeedingCare(Agent parent, VillageAI village) {
        return village.agentManager().all().stream()
                .filter(child -> !child.isAdult())
                .filter(child -> parent.villagerUuid().equals(child.parentA()) || parent.villagerUuid().equals(child.parentB()))
                .anyMatch(child -> child.needLevel(NeedType.HUNGER) >= 55 || child.needLevel(NeedType.SOCIAL) >= 55);
    }

    private boolean canSeekPartner(Agent agent, VillageAI village, long currentTick) {
        if (!agent.isAdult()) return false;
        if (village.population() >= village.maxPopulation() || village.population() >= village.populationTarget()) return false;
        if (village.bedCount() < village.population() + 1) return false;
        if (village.foodStock() < Math.max(8, village.population() * 2)) return false;
        if (village.threatDetected()) return false;
        return currentTick - agent.lastReproductionTick() >= REPRODUCTION_COOLDOWN_TICKS;
    }

    private boolean isDay(VillageAI village) {
        return village.center().getWorld() != null && village.center().getWorld().isDayTime();
    }

    private boolean shouldHaveNightShelter(VillageAI village) {
        return village.center().getWorld() != null && !village.center().getWorld().isDayTime();
    }

    private Candidate max(Candidate first, Candidate second) {
        return second.priority() > first.priority() ? second : first;
    }

    private long currentTick() {
        try {
            return Bukkit.getCurrentTick();
        } catch (IllegalStateException | NullPointerException exception) {
            return 0L;
        }
    }

    private record Candidate(AgentGoal goal, int priority, String reason) {}
}
