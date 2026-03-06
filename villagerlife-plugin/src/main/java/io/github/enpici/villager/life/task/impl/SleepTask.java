package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;

public class SleepTask extends BaseTask {

    public SleepTask() {
        super("sleep", 100L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return villageAI.bedCount() > 0;
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        agent.adjustNeed(NeedType.ENERGY, -30);
        return TaskStatus.SUCCESS;
    }
}
