package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Material;

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
        VillageAI.MaterialRequest request = villageAI.pollMaterialRequest();
        if (request != null) {
            int deposit = Math.max(1, Math.min(4, request.amount()));
            villageAI.addMaterialStock(request.material(), deposit);
            int remaining = request.amount() - deposit;
            if (remaining > 0) {
                villageAI.requeueMaterialRequest(new VillageAI.MaterialRequest(request.material(), remaining));
            }
        } else {
            villageAI.addMaterialStock(Material.COBBLESTONE, 2);
            villageAI.addMaterialStock(Material.OAK_PLANKS, 2);
        }
        return TaskStatus.SUCCESS;
    }
}
