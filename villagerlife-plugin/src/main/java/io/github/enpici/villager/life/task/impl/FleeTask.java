package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;

public class FleeTask extends BaseTask {

    public FleeTask() {
        super("flee", 80L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return villageAI.threatDetected();
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        agent.adjustNeed(NeedType.SAFETY, -20);
        return TaskStatus.SUCCESS;
    }
}
