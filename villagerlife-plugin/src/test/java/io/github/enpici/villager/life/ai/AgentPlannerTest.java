package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentGoal;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.agent.LifeStage;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPlannerTest {

    @Test
    void hungerBecomesSurvivalGoal() {
        Agent agent = new Agent(UUID.randomUUID(), AgentRole.FARMER);
        agent.setNeed(NeedType.HUNGER, 90);
        VillageAI village = villageWith(agent);

        new AgentPlanner().plan(agent, village, 200L);

        assertEquals(AgentGoal.SURVIVE_HUNGER, agent.currentGoal());
        assertEquals(95, agent.currentGoalPriority());
        assertEquals("hunger is critical", agent.currentGoalReason());
        assertEquals(200L, agent.currentGoalStartedTick());
    }

    @Test
    void childChoosesGrowthGoalBeforeRoleWork() {
        Agent agent = new Agent(UUID.randomUUID(), AgentRole.FARMER);
        agent.setLifeStage(LifeStage.CHILD);
        agent.setNeed(NeedType.HUNGER, 0);
        VillageAI village = villageWith(agent);
        village.setFoodStock(0);

        new AgentPlanner().plan(agent, village, 10L);

        assertEquals(AgentGoal.GROW_UP, agent.currentGoal());
    }

    @Test
    void builderPrioritizesHousingWhenBedsAreMissing() {
        Agent builder = new Agent(UUID.randomUUID(), AgentRole.BUILDER);
        Agent farmer = new Agent(UUID.randomUUID(), AgentRole.FARMER);
        AgentManager manager = new AgentManager(null);
        manager.restore(builder);
        manager.restore(farmer);
        VillageAI village = new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), manager, null);
        village.setBedCount(1);
        village.setFoodStock(50);

        new AgentPlanner().plan(builder, village, 10L);

        assertEquals(AgentGoal.BUILD_HOUSING, builder.currentGoal());
        assertTrue(builder.currentGoalPriority() >= 78);
    }

    @Test
    void crafterChoosesCraftingWhenRawMaterialsExist() {
        Agent crafter = new Agent(UUID.randomUUID(), AgentRole.CRAFTER);
        VillageAI village = villageWith(crafter);
        village.addMaterialStock(Material.OAK_LOG, 2);

        new AgentPlanner().plan(crafter, village, 10L);

        assertEquals(AgentGoal.CRAFT_SUPPLIES, crafter.currentGoal());
    }

    @Test
    void traderChoosesTradeGoal() {
        Agent trader = new Agent(UUID.randomUUID(), AgentRole.TRADER);
        VillageAI village = villageWith(trader);

        new AgentPlanner().plan(trader, village, 10L);

        assertEquals(AgentGoal.TRADE_GOODS, trader.currentGoal());
    }

    private VillageAI villageWith(Agent agent) {
        AgentManager manager = new AgentManager(null);
        manager.restore(agent);
        return new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), manager, null);
    }
}
