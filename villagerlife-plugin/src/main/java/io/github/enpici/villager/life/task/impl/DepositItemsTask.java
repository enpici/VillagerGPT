package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;

public class DepositItemsTask extends BaseTask {

    public DepositItemsTask() {
        super("deposit_items", 60L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return true;
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        if (WorldItemInteraction.hasCarriedItems(agent)) {
            TaskStatus deposit = WorldItemInteraction.depositInventoryToNearestContainer(agent, villageAI, 20);
            if (deposit == TaskStatus.SUCCESS || deposit == TaskStatus.RUNNING) {
                return deposit;
            }
        }

        TaskStatus pickup = WorldItemInteraction.collectNearbyItem(agent, villageAI, 12.0, material -> true);
        if (pickup == TaskStatus.SUCCESS || pickup == TaskStatus.RUNNING) {
            return pickup;
        }

        VillageAI.MaterialRequest request = villageAI.pollMaterialRequest();
        if (request != null) {
            villageAI.requeueMaterialRequest(request);
            agent.setLastEvent("world:no_items_for_request:" + request.material().name().toLowerCase());
        }
        return TaskStatus.RETRYABLE_FAILED;
    }
}
