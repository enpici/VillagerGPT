package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.VillagerLifePlugin;
import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.integration.CitizensGateway;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Villager;

public class HarvestTask extends BaseTask {

    private Location target;
    private int ticks;

    public HarvestTask() {
        super("harvest", 120L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return true;
    }

    @Override
    protected void onStart(Agent agent, VillageAI villageAI) {
        Villager villager = TaskMovement.villager(agent);
        target = TaskMovement.randomNearby(villageAI.center(), 10.0);
        TaskMovement.moveTo(villager, target, 0.8);
        ticks = 0;
        agent.setLastEvent("moving:harvest");
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        ticks++;
        Villager villager = TaskMovement.villager(agent);
        TaskStatus pickup = WorldItemInteraction.collectNearbyItem(agent, villageAI, 8.0, this::isFoodOrNaturalMaterial);
        if (pickup == TaskStatus.SUCCESS || pickup == TaskStatus.RUNNING) {
            return pickup;
        }

        TaskStatus harvest = WorldItemInteraction.harvestMatureCrop(agent, villageAI, 16);
        if (harvest == TaskStatus.SUCCESS || harvest == TaskStatus.RUNNING) {
            agent.adjustNeed(NeedType.HUNGER, -5);
            return harvest;
        }

        if (ticks % 20 == 0) {
            TaskMovement.moveTo(villager, target, 0.8);
        }
        if (!TaskMovement.reached(villager, target, 4.0) && ticks < 80) {
            return TaskStatus.RUNNING;
        }

        if (villageAI.center().getWorld() != null) {
            agent.setLastEvent("world:no_food_source");
            return TaskStatus.RETRYABLE_FAILED;
        }

        VillagerLifePlugin plugin = VillagerLifePlugin.instance();
        if (plugin != null && plugin.isCitizensIntegrationEnabled()) {
            CitizensGateway adapter = plugin.citizensAdapter();
            if (villager != null && adapter.getOrCreateNpc(villager) != null) {
                adapter.playSwingAnimation();
            }
        } else if (villager != null) {
            villager.swingMainHand();
        }

        villageAI.addFoodStock(2);
        VillageAI.MaterialRequest request = villageAI.pollMaterialRequest();
        if (request != null) {
            int gathered = Math.max(1, Math.min(3, request.amount()));
            WorldItemInteraction.addGatheredItem(agent, villageAI, request.material(), gathered);
            int remaining = request.amount() - gathered;
            if (remaining > 0) {
                villageAI.requeueMaterialRequest(new VillageAI.MaterialRequest(request.material(), remaining));
            }
        } else {
            WorldItemInteraction.addGatheredItem(agent, villageAI, Material.WHEAT, 2);
            WorldItemInteraction.addGatheredItem(agent, villageAI, Material.OAK_LOG, 1);
        }

        agent.adjustNeed(NeedType.HUNGER, -5);
        return TaskStatus.SUCCESS;
    }

    private boolean isFoodOrNaturalMaterial(Material material) {
        return material == Material.WHEAT
                || material == Material.BREAD
                || material == Material.CARROT
                || material == Material.POTATO
                || material == Material.BEETROOT
                || material == Material.OAK_LOG
                || material == Material.OAK_PLANKS
                || material == Material.STICK
                || material == Material.WHEAT_SEEDS;
    }
}
