package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.task.Task;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.task.impl.InterceptThreatTask;
import io.github.enpici.villager.life.task.impl.PatrolTask;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class GuardDecisionEngineStateTransitionTest {

    private final DecisionEngine decisionEngine = new DecisionEngine();

    @Test
    void guardTransitionsPatrolToInterceptToIdleState() {
        VillageAI village = new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), new AgentManager(null), (BlueprintService) null);
        Agent guard = new Agent(UUID.randomUUID(), AgentRole.GUARD);

        Task patrol = decisionEngine.decide(guard, village);
        assertInstanceOf(PatrolTask.class, patrol);

        village.registerThreatSignal("world-threat", village.center(), 200L);
        Task intercept = decisionEngine.decide(guard, village);
        assertInstanceOf(InterceptThreatTask.class, intercept);

        guard.assignTask(intercept);
        guard.clearTask(TaskStatus.SUCCESS);
        assertNull(guard.activeTask(), "sin tarea activa, el agente está en idle");
        assertEquals("task:success", guard.lastEvent());
    }
}
