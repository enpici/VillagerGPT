package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentGoal;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.agent.LifeStage;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.task.impl.EatTask;
import io.github.enpici.villager.life.task.impl.HarvestTask;
import io.github.enpici.villager.life.task.impl.CraftSuppliesTask;
import io.github.enpici.villager.life.task.impl.LeadVillageTask;
import io.github.enpici.villager.life.task.impl.MineResourcesTask;
import io.github.enpici.villager.life.task.impl.PatrolTask;
import io.github.enpici.villager.life.task.impl.TradeTask;
import io.github.enpici.villager.life.task.impl.WanderTask;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DecisionEngineLifecycleTest {

    @Test
    void choosesEatingWhenHungerDominates() {
        Agent agent = new Agent(UUID.randomUUID(), AgentRole.FARMER);
        agent.setNeed(NeedType.HUNGER, 90);
        VillageAI village = new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), new AgentManager(null), null);

        assertInstanceOf(EatTask.class, new DecisionEngine().decide(agent, village));
        assertEquals("hunger need", agent.lastDecisionReason());
    }

    @Test
    void criticalHungerForagesWhenThereIsNoFoodToEat() {
        Agent agent = new Agent(UUID.randomUUID(), AgentRole.FARMER);
        agent.assignGoal(AgentGoal.SURVIVE_HUNGER, 95, "hunger is critical", 10L);
        VillageAI village = new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), new AgentManager(null), null);
        village.setFoodStock(0);

        assertInstanceOf(HarvestTask.class, new DecisionEngine().decide(agent, village));
        assertEquals("goal survive_hunger: hunger is critical", agent.lastDecisionReason());
    }

    @Test
    void starvingTraderTradesForFoodInsteadOfBlockingOnEating() {
        Agent agent = new Agent(UUID.randomUUID(), AgentRole.TRADER);
        agent.assignGoal(AgentGoal.SURVIVE_HUNGER, 95, "hunger is critical", 10L);
        VillageAI village = new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), new AgentManager(null), null);
        village.setFoodStock(0);

        assertInstanceOf(TradeTask.class, new DecisionEngine().decide(agent, village));
        assertEquals("goal survive_hunger: hunger is critical", agent.lastDecisionReason());
    }

    @Test
    void childDoesNotChooseRoleWork() {
        Agent agent = new Agent(UUID.randomUUID(), AgentRole.FARMER);
        agent.setLifeStage(LifeStage.CHILD);
        agent.setNeed(NeedType.HUNGER, 0);
        agent.setNeed(NeedType.ENERGY, 0);
        agent.setNeed(NeedType.SAFETY, 0);
        agent.setNeed(NeedType.SOCIAL, 0);
        VillageAI village = new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), new AgentManager(null), null);

        assertInstanceOf(WanderTask.class, new DecisionEngine().decide(agent, village));
        assertEquals("child social growth", agent.lastDecisionReason());
    }

    @Test
    void plannedFoodWorkBecomesHarvestTask() {
        Agent agent = new Agent(UUID.randomUUID(), AgentRole.FARMER);
        agent.assignGoal(AgentGoal.WORK_FOOD, 70, "maintaining food reserves", 30L);
        VillageAI village = new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), new AgentManager(null), null);

        assertInstanceOf(HarvestTask.class, new DecisionEngine().decide(agent, village));
        assertEquals("goal work_food: maintaining food reserves", agent.lastDecisionReason());
    }

    @Test
    void plannedRoleGoalsBecomeDistinctPlayerLikeTasks() {
        VillageAI village = new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), new AgentManager(null), null);
        DecisionEngine engine = new DecisionEngine();

        assertInstanceOf(MineResourcesTask.class, decideForGoal(engine, village, AgentRole.MINER, AgentGoal.GATHER_MATERIALS));
        assertInstanceOf(CraftSuppliesTask.class, decideForGoal(engine, village, AgentRole.CRAFTER, AgentGoal.CRAFT_SUPPLIES));
        assertInstanceOf(TradeTask.class, decideForGoal(engine, village, AgentRole.TRADER, AgentGoal.TRADE_GOODS));
        assertInstanceOf(LeadVillageTask.class, decideForGoal(engine, village, AgentRole.LEADER, AgentGoal.MANAGE_VILLAGE));
        assertInstanceOf(PatrolTask.class, decideForGoal(engine, village, AgentRole.GUARD, AgentGoal.PATROL));
    }

    private io.github.enpici.villager.life.task.Task decideForGoal(
            DecisionEngine engine,
            VillageAI village,
            AgentRole role,
            AgentGoal goal
    ) {
        Agent agent = new Agent(UUID.randomUUID(), role);
        agent.assignGoal(goal, 60, "test goal", 1L);
        return engine.decide(agent, village);
    }
}
