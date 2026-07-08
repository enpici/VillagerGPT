package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.PhysicalResourceScanner;
import io.github.enpici.villager.life.village.VillageAI;

public class EatTask extends BaseTask {

    private final PhysicalResourceScanner physicalResourceScanner = new PhysicalResourceScanner();

    public EatTask() {
        super("eat", 40L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return villageAI.foodStock() > 0;
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        int consumed = physicalResourceScanner.consumeFood(villageAI, villageAI.agentManager().all(), agent, 1, 24);
        if (consumed <= 0) {
            return TaskStatus.FAILED;
        }
        agent.adjustNeed(NeedType.HUNGER, -35);
        agent.setLastEvent("need:ate_food:" + consumed);
        return TaskStatus.SUCCESS;
    }
}
