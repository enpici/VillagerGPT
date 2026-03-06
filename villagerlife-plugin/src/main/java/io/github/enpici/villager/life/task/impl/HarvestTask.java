package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;

public class HarvestTask extends BaseTask {

    public HarvestTask() {
        super("harvest", 120L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return true;
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        villageAI.addFoodStock(2);
        agent.adjustNeed(NeedType.HUNGER, -5);
        return TaskStatus.SUCCESS;
    }
}
