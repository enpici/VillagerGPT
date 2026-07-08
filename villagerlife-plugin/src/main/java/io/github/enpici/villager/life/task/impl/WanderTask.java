package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.bukkit.entity.Villager;

public class WanderTask extends BaseTask {

    private Location target;
    private int ticks;

    public WanderTask() {
        super("wander", 60L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return true;
    }

    @Override
    protected void onStart(Agent agent, VillageAI villageAI) {
        Villager villager = TaskMovement.villager(agent);
        Location origin = villager != null ? villager.getLocation() : villageAI.center();
        target = TaskMovement.randomNearby(origin, 8.0);
        TaskMovement.moveTo(villager, target, 0.75);
        ticks = 0;
        agent.setLastEvent("moving:wander");
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        ticks++;
        Villager villager = TaskMovement.villager(agent);
        if (ticks % 20 == 0) {
            TaskMovement.moveTo(villager, target, 0.75);
        }
        agent.adjustNeed(NeedType.SOCIAL, -1);
        if (TaskMovement.reached(villager, target, 3.0) || ticks >= 60) {
            agent.setLastEvent("role:wandered");
            return TaskStatus.SUCCESS;
        }
        return TaskStatus.RUNNING;
    }
}
