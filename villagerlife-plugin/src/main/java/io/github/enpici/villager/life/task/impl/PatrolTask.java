package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.bukkit.entity.Villager;

public class PatrolTask extends BaseTask {

    public PatrolTask() {
        super("patrol", 120L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return villageAI.center().getWorld() != null;
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        Villager villager = agent.resolveVillager();
        if (villager == null || !villager.isValid()) {
            return TaskStatus.FAILED;
        }

        Location center = villageAI.center();
        double angle = (org.bukkit.Bukkit.getCurrentTick() % 360) * Math.PI / 180.0;
        Location perimeterPoint = center.clone().add(Math.cos(angle) * 8.0, 0, Math.sin(angle) * 8.0);
        villager.getPathfinder().moveTo(perimeterPoint);

        return TaskStatus.SUCCESS;
    }
}
