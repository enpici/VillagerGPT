package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentGoal;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.minecraft.MinecraftKnowledge;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.task.Task;
import io.github.enpici.villager.life.task.impl.*;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Bukkit;
import org.bukkit.Material;

public class DecisionEngine {

    private final MinecraftKnowledge minecraftKnowledge = new MinecraftKnowledge();

    public Task decide(Agent agent, VillageAI village) {
        if (agent.role() == AgentRole.GUARD) {
            var villager = Bukkit.getServer() != null ? agent.resolveVillager() : null;
            if (village.threatDetected() && village.activeThreatLocation().isPresent()) {
                return choose(agent, new InterceptThreatTask(), "guard intercepting active threat");
            }
            if (agent.currentGoalPriority() <= 0
                    && villager != null
                    && villager.getLocation().distanceSquared(village.center()) > 36) {
                return choose(agent, new ReturnToPostTask(), "guard returning to village post");
            }
            if (agent.currentGoalPriority() <= 0) {
                return choose(agent, new PatrolTask(), "guard patrol");
            }
        }

        if (agent.currentGoalPriority() > 0) {
            return decideFromGoal(agent, village);
        }

        double eat = agent.needLevel(NeedType.HUNGER) * 1.4;
        double sleep = agent.needLevel(NeedType.ENERGY) * 1.2;
        double flee = village.threatDetected() ? 95 : agent.needLevel(NeedType.SAFETY);
        double socialize = agent.needLevel(NeedType.SOCIAL);
        double work = agent.isAdult() ? scoreWork(agent.role(), village) : 0;

        double max = Math.max(Math.max(eat, sleep), Math.max(flee, Math.max(socialize, work)));
        if (max <= 0) {
            return choose(agent, new WanderTask(), agent.isAdult() ? "idle exploration" : "child social growth");
        }
        if (max == flee) return choose(agent, new FleeTask(), "safety need or village threat");
        if (max == eat) return choose(agent, hungerSurvivalTask(agent, village), "hunger need");
        if (max == sleep) return choose(agent, new SleepTask(), "energy need");
        if (!agent.isAdult()) return choose(agent, new WanderTask(), "child social growth");
        if (max == socialize && currentTick() % 2 == 0) return choose(agent, new WanderTask(), "social need");

        return switch (agent.role()) {
            case FARMER -> choose(agent, new HarvestTask(), "farmer food production");
            case BUILDER -> !village.pendingMaterials().isEmpty()
                    ? choose(agent, materialPreparationTask(village), "builder material request")
                    : choose(agent, new WanderTask(), "builder waiting for build work");
            case MINER -> choose(agent, new MineResourcesTask(), "miner resource gathering");
            case CRAFTER -> choose(agent, new CraftSuppliesTask(), "crafter material preparation");
            case TRADER -> choose(agent, new TradeTask(), "trader exchange route");
            case LEADER -> choose(agent, new LeadVillageTask(), "leader village oversight");
            case GUARD -> choose(agent, guardDefaultTask(agent, village), "guard patrol");
        };
    }

    private double scoreWork(AgentRole role, VillageAI village) {
        double dayBonus = village.center().getWorld() != null && village.center().getWorld().isDayTime() ? 15 : 5;
        return switch (role) {
            case FARMER -> 20 * role.foodPriority() + dayBonus;
            case BUILDER -> 20 * role.buildPriority() + (village.bedCount() < village.population() ? 12 : 0);
            case GUARD -> 20 * role.defensePriority() + (village.threatDetected() ? 25 : 0);
            default -> 10 + dayBonus;
        };
    }

    private Task decideFromGoal(Agent agent, VillageAI village) {
        String reason = "goal " + agent.currentGoal().name().toLowerCase() + ": " + agent.currentGoalReason();
        AgentGoal goal = agent.currentGoal();
        return switch (goal) {
            case SURVIVE_HUNGER -> choose(agent, hungerSurvivalTask(agent, village), reason);
            case REST -> choose(agent, new SleepTask(), reason);
            case SEEK_SAFETY -> choose(agent, new FleeTask(), reason);
            case WORK_FOOD -> choose(agent, new HarvestTask(), reason);
            case BUILD_SHELTER -> choose(agent, buildTaskFor(village), reason);
            case BUILD_HOUSING -> choose(agent, buildTaskFor(village), reason);
            case GATHER_MATERIALS -> choose(agent, new MineResourcesTask(), reason);
            case CRAFT_SUPPLIES -> choose(agent, new CraftSuppliesTask(), reason);
            case PATROL -> choose(agent, new PatrolTask(), reason);
            case TRADE_GOODS -> choose(agent, new TradeTask(), reason);
            case MANAGE_VILLAGE -> choose(agent, new LeadVillageTask(), reason);
            case SEEK_PARTNER -> choose(agent, new SeekPartnerTask(), reason);
            case RAISE_CHILD -> choose(agent, new CareForChildTask(), reason);
            case SOCIALIZE, GROW_UP, IDLE -> choose(agent, new WanderTask(), reason);
        };
    }

    private Task buildTaskFor(VillageAI village) {
        if (village.blueprintService() != null && village.pendingBlueprintCount() > 0) {
            var nextBlueprint = village.pendingBlueprintsSnapshot(1).stream().findFirst();
            if (nextBlueprint.isPresent()) {
                return new BuildStructureTask(nextBlueprint.get());
            }
        }
        if (!village.pendingMaterials().isEmpty()) {
            return materialPreparationTask(village);
        }
        return new WanderTask();
    }

    private Task hungerSurvivalTask(Agent agent, VillageAI village) {
        if (village.foodStock() > 0) {
            return new EatTask();
        }
        if (!agent.isAdult()) {
            return new WanderTask();
        }
        if (agent.role() == AgentRole.TRADER) {
            return new TradeTask();
        }
        return new HarvestTask();
    }

    private Task materialPreparationTask(VillageAI village) {
        if (canCraftPendingMaterial(village)) {
            return new CraftSuppliesTask();
        }
        return new MineResourcesTask();
    }

    private Task guardDefaultTask(Agent agent, VillageAI village) {
        var villager = Bukkit.getServer() != null ? agent.resolveVillager() : null;
        if (villager != null && villager.getLocation().distanceSquared(village.center()) > 36) {
            return new ReturnToPostTask();
        }
        return new PatrolTask();
    }

    private boolean canCraftPendingMaterial(VillageAI village) {
        for (Material material : village.pendingMaterials().keySet()) {
            if (material == Material.STONE
                    && village.availableMaterial(Material.COBBLESTONE) > 0
                    && village.availableMaterial(Material.FURNACE) > 0
                    && village.availableMaterial(Material.COAL) > 0) {
                return true;
            }
            var inputs = minecraftKnowledge.craftingInputs(material);
            if (!inputs.isEmpty()
                    && inputs.entrySet().stream().allMatch(entry -> village.availableMaterial(entry.getKey()) >= entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private Task choose(Agent agent, Task task, String reason) {
        agent.setLastDecisionReason(reason);
        return task;
    }

    private long currentTick() {
        try {
            return Bukkit.getCurrentTick();
        } catch (IllegalStateException | NullPointerException exception) {
            return 0L;
        }
    }
}
