package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Material;

public class TradeTask extends BaseTask {

    public TradeTask() {
        super("trade", 120L);
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return agent.isAdult();
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        if (villageAI.foodStock() < Math.max(12, villageAI.population() * 3)) {
            WorldItemInteraction.addGatheredItem(agent, villageAI, Material.BREAD, 2);
            WorldItemInteraction.addGatheredItem(agent, villageAI, Material.EMERALD, 1);
            agent.setLastEvent("role:traded_for_food");
        } else if (new io.github.enpici.villager.life.village.PhysicalResourceScanner()
                .consumeFood(villageAI, villageAI.agentManager().all(), agent, 2, 24) > 0) {
            WorldItemInteraction.addGatheredItem(agent, villageAI, Material.EMERALD, 2);
            WorldItemInteraction.addGatheredItem(agent, villageAI, Material.OAK_PLANKS, 2);
            agent.setLastEvent("role:traded_surplus");
        } else {
            WorldItemInteraction.addGatheredItem(agent, villageAI, Material.EMERALD, 1);
            agent.setLastEvent("role:bartered");
        }

        agent.adjustNeed(NeedType.SOCIAL, -12);
        agent.adjustNeed(NeedType.ENERGY, 2);
        return TaskStatus.SUCCESS;
    }
}
