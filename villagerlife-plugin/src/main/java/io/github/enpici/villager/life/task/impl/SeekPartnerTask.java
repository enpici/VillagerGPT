package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;

public class SeekPartnerTask extends BaseTask {

    public SeekPartnerTask() {
        super("seek_partner", 100L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return agent.isAdult()
                && villageAI.population() < villageAI.populationTarget()
                && villageAI.bedCount() >= villageAI.population() + 1
                && villageAI.foodStock() >= Math.max(8, villageAI.population() * 2);
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        agent.adjustNeed(NeedType.SOCIAL, -10);
        agent.adjustNeed(NeedType.ENERGY, 1);
        agent.setLastEvent("family:seeking_partner");
        return TaskStatus.SUCCESS;
    }
}
