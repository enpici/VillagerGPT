package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.task.Task;
import io.github.enpici.villager.life.task.impl.*;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Bukkit;

public class DecisionEngine {

    public Task decide(Agent agent, VillageAI village) {
        if (agent.role() == AgentRole.GUARD) {
            var villager = Bukkit.getServer() != null ? agent.resolveVillager() : null;
            if (village.threatDetected()) {
                return new InterceptThreatTask();
            }
            if (villager != null && villager.getLocation().distanceSquared(village.center()) > 36) {
                return new ReturnToPostTask();
            }
            return new PatrolTask();
        }

        double eat = agent.needLevel(NeedType.HUNGER) * 1.4;
        double sleep = agent.needLevel(NeedType.ENERGY) * 1.2;
        double flee = village.threatDetected() ? 95 : agent.needLevel(NeedType.SAFETY);
        double socialize = agent.needLevel(NeedType.SOCIAL);
        double work = scoreWork(agent.role(), village);

        double max = Math.max(Math.max(eat, sleep), Math.max(flee, Math.max(socialize, work)));
        if (max == flee) return new FleeTask();
        if (max == eat) return new EatTask();
        if (max == sleep) return new SleepTask();
        if (max == socialize && Bukkit.getCurrentTick() % 2 == 0) return new WanderTask();

        return switch (agent.role()) {
            case FARMER -> new HarvestTask();
            case BUILDER -> !village.pendingMaterials().isEmpty() ? new DepositItemsTask() : new WanderTask();
            default -> new WanderTask();
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
}
