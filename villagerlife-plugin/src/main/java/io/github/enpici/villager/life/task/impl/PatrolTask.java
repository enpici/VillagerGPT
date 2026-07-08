package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.bukkit.entity.Villager;

public class PatrolTask extends BaseTask {

    private Location target;
    private int ticks;

    public PatrolTask() {
        super("patrol", 100L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return agent.isAdult() && villageAI.center().getWorld() != null;
    }

    @Override
    protected void onStart(Agent agent, VillageAI villageAI) {
        Villager villager = TaskMovement.villager(agent);
        target = villageAI.activeThreatLocation().orElseGet(() -> TaskMovement.randomNearby(villageAI.center(), 14.0));
        TaskMovement.moveTo(villager, target, 0.9);
        ticks = 0;
        agent.setLastEvent("moving:patrol");
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        ticks++;
        Villager villager = TaskMovement.villager(agent);
        if (ticks % 20 == 0) {
            TaskMovement.moveTo(villager, target, 0.9);
        }
        agent.adjustNeed(NeedType.SAFETY, -1);
        if (ticks % 20 == 0) {
            agent.adjustNeed(NeedType.ENERGY, 1);
        }
        if (TaskMovement.reached(villager, target, 4.0) || ticks >= 100) {
            agent.setLastEvent(villageAI.threatDetected() ? "role:guarded_threat" : "role:patrolled");
            return TaskStatus.SUCCESS;
        }
        return TaskStatus.RUNNING;
    }
}
