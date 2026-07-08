package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.PhysicalResourceScanner;
import io.github.enpici.villager.life.village.VillageAI;

public class CareForChildTask extends BaseTask {

    private final PhysicalResourceScanner physicalResourceScanner = new PhysicalResourceScanner();

    public CareForChildTask() {
        super("care_for_child", 80L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return agent.isAdult();
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        Agent child = villageAI.agentManager().all().stream()
                .filter(candidate -> !candidate.isAdult())
                .filter(candidate -> agent.villagerUuid().equals(candidate.parentA())
                        || agent.villagerUuid().equals(candidate.parentB()))
                .findFirst()
                .orElse(null);
        if (child == null) {
            return TaskStatus.FAILED;
        }

        if (villageAI.foodStock() > 0 && child.needLevel(NeedType.HUNGER) >= 35
                && physicalResourceScanner.consumeFood(villageAI, villageAI.agentManager().all(), agent, 1, 24) > 0) {
            child.adjustNeed(NeedType.HUNGER, -25);
        }
        child.adjustNeed(NeedType.SOCIAL, -20);
        agent.adjustNeed(NeedType.SOCIAL, -5);
        agent.setLastEvent("family:cared_for_child");
        child.setLastEvent("family:received_care");
        return TaskStatus.SUCCESS;
    }
}
