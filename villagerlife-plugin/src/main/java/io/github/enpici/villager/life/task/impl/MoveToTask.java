package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.VillagerLifePlugin;
import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.integration.CitizensAdapter;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.bukkit.entity.Villager;

public class MoveToTask extends BaseTask {

    private final Location target;

    public MoveToTask(Location target) {
        super("move_to", 200L);
        this.target = target;
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return target != null;
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        Villager villager = agent.resolveVillager();
        if (villager == null || !villager.isValid()) return TaskStatus.FAILED;
        if (villager.getLocation().distanceSquared(target) <= 4) return TaskStatus.SUCCESS;

        VillagerLifePlugin plugin = VillagerLifePlugin.instance();
        if (plugin != null && plugin.isCitizensIntegrationEnabled()) {
            CitizensAdapter adapter = plugin.citizensAdapter();
            if (adapter.getOrCreateNpc(villager) != null && adapter.navigateTo(target)) {
                return TaskStatus.RUNNING;
            }
        }

        villager.getPathfinder().moveTo(target);
        return TaskStatus.RUNNING;
    }
}
