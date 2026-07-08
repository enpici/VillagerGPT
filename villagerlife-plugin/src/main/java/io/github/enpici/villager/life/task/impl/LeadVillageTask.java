package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;

public class LeadVillageTask extends BaseTask {

    public LeadVillageTask() {
        super("lead_village", 100L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return agent.isAdult();
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        villageAI.planVillage();
        villageAI.ensureBasicNeedsForGrowth();
        agent.adjustNeed(NeedType.SOCIAL, -6);
        agent.adjustNeed(NeedType.ENERGY, 2);
        agent.setLastEvent("role:planned_village");
        return TaskStatus.SUCCESS;
    }
}
