package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.VillagerLifePlugin;
import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.integration.CitizensAdapter;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Material;
import org.bukkit.entity.Villager;

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
        VillagerLifePlugin plugin = VillagerLifePlugin.instance();
        if (plugin != null && plugin.isCitizensIntegrationEnabled()) {
            CitizensAdapter adapter = plugin.citizensAdapter();
            Villager villager = agent.resolveVillager();
            if (villager != null && adapter.getOrCreateNpc(villager) != null) {
                adapter.playSwingAnimation();
            }
        }

        villageAI.addFoodStock(2);
        VillageAI.MaterialRequest request = villageAI.pollMaterialRequest();
        if (request != null) {
            int gathered = Math.max(1, Math.min(3, request.amount()));
            villageAI.addMaterialStock(request.material(), gathered);
            int remaining = request.amount() - gathered;
            if (remaining > 0) {
                villageAI.requeueMaterialRequest(new VillageAI.MaterialRequest(request.material(), remaining));
            }
        } else {
            villageAI.addMaterialStock(Material.WHEAT, 2);
            villageAI.addMaterialStock(Material.OAK_LOG, 1);
        }

        agent.adjustNeed(NeedType.HUNGER, -5);
        return TaskStatus.SUCCESS;
    }
}
