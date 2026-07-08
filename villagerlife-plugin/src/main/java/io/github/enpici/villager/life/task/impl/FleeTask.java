package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Villager;

public class FleeTask extends BaseTask {

    private Location target;
    private int ticks;

    public FleeTask() {
        super("flee", 80L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return villageAI.threatDetected() || agent.needLevel(NeedType.SAFETY) >= 60;
    }

    @Override
    protected void onStart(Agent agent, VillageAI villageAI) {
        Villager villager = TaskMovement.villager(agent);
        Monster threat = TaskMovement.nearestMonster(villager, 16.0);
        target = threat != null
                ? TaskMovement.awayFrom(threat, villager, 14.0)
                : TaskMovement.randomNearby(villager != null ? villager.getLocation() : villageAI.center(), 12.0);
        TaskMovement.moveTo(villager, target, 1.15);
        ticks = 0;
        agent.setLastEvent(threat != null ? "moving:flee:" + threat.getType().name().toLowerCase() : "moving:flee");
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        ticks++;
        Villager villager = TaskMovement.villager(agent);
        if (ticks % 15 == 0) {
            Monster threat = TaskMovement.nearestMonster(villager, 16.0);
            if (threat != null) {
                target = TaskMovement.awayFrom(threat, villager, 14.0);
            }
            TaskMovement.moveTo(villager, target, 1.15);
        }
        agent.adjustNeed(NeedType.SAFETY, -2);
        if (TaskMovement.reached(villager, target, 4.0) || ticks >= 80) {
            agent.setLastEvent("role:fled");
            return TaskStatus.SUCCESS;
        }
        return TaskStatus.RUNNING;
    }
}
