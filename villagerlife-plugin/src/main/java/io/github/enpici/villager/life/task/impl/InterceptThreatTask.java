package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Villager;

public class InterceptThreatTask extends BaseTask {

    public InterceptThreatTask() {
        super("intercept_threat", 100L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return villageAI.threatDetected() && villageAI.activeThreatLocation().isPresent();
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        Villager villager = agent.resolveVillager();
        if (villager == null || !villager.isValid()) {
            return TaskStatus.FAILED;
        }

        Location threat = villageAI.activeThreatLocation().orElse(villageAI.center());
        villager.getPathfinder().moveTo(threat);
        agent.setLastEvent("defense:intercepting:" + threat.getBlockX() + "," + threat.getBlockZ());

        if (villager.getLocation().distanceSquared(threat) <= 9) {
            villageAI.clearThreatSignal("event:world-threat");
            Bukkit.getPluginManager().callEvent(new io.github.enpici.villager.life.event.AgentThreatDetectedEvent(agent, "guard-intercept"));
            return TaskStatus.SUCCESS;
        }

        return TaskStatus.RUNNING;
    }
}
