package io.github.enpici.villager.life.agent;

import io.github.enpici.villager.life.role.AgentRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentLifecycleTest {

    @Test
    void childBecomesAdultWhenAgeThresholdIsReached() {
        Agent agent = new Agent(UUID.randomUUID(), AgentRole.FARMER);
        agent.setLifeStage(LifeStage.CHILD);
        agent.setAgeTicks(9);

        assertTrue(agent.tickAge(10));
        assertEquals(LifeStage.ADULT, agent.lifeStage());
        assertEquals("life:adult", agent.lastEvent());
    }

    @Test
    void needsAndSkillsAreClampedAndTracked() {
        Agent agent = new Agent(UUID.randomUUID(), AgentRole.BUILDER);

        agent.setNeed(NeedType.HUNGER, 150);
        agent.adjustNeed(NeedType.ENERGY, -100);
        agent.addSkillXp(AgentRole.BUILDER, 3);

        assertEquals(100, agent.needLevel(NeedType.HUNGER));
        assertEquals(0, agent.needLevel(NeedType.ENERGY));
        assertEquals(3, agent.skillXp(AgentRole.BUILDER));
    }

    @Test
    void goalStateIsClampedAndExplained() {
        Agent agent = new Agent(UUID.randomUUID(), AgentRole.GUARD);

        boolean changed = agent.assignGoal(AgentGoal.PATROL, 150, "guard patrol duty", 42L);

        assertTrue(changed);
        assertEquals(AgentGoal.PATROL, agent.currentGoal());
        assertEquals(100, agent.currentGoalPriority());
        assertEquals("guard patrol duty", agent.currentGoalReason());
        assertEquals(42L, agent.currentGoalStartedTick());
        assertEquals("goal:patrol", agent.lastEvent());
    }
}
