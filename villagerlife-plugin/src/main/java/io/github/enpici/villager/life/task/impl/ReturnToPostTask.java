package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.entity.Villager;

public class ReturnToPostTask extends BaseTask {

    public ReturnToPostTask() {
        super("return_to_post", 120L);
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
        var center = villageAI.center();
        villager.getPathfinder().moveTo(center);

        if (villager.getLocation().distanceSquared(center) <= 4) {
            agent.setLastEvent("defense:post-secured");
            return TaskStatus.SUCCESS;
        }
        return TaskStatus.RUNNING;
    }
}
