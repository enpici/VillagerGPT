package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;

public class EatTask extends BaseTask {

    public EatTask() {
        super("eat", 40L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return villageAI.foodStock() > 0;
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        if (!villageAI.consumeFood(1)) {
            return TaskStatus.FAILED;
        }
        agent.adjustNeed(NeedType.HUNGER, -35);
        return TaskStatus.SUCCESS;
    }
}
