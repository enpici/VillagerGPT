package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleAssignmentServiceTest {

    @Test
    void assignsFarmerWhenFoodIsLow() {
        VillageAI village = villageWithAgent(AgentRole.BUILDER);
        village.setFoodStock(1);

        Agent agent = village.agentManager().all().iterator().next();

        assertEquals(AgentRole.FARMER, new RoleAssignmentService().chooseRoleForAdult(agent, village));
    }

    @Test
    void assignsBuilderWhenBedsAreBelowPopulation() {
        AgentManager manager = new AgentManager(null);
        Agent first = new Agent(UUID.randomUUID(), AgentRole.FARMER);
        Agent second = new Agent(UUID.randomUUID(), AgentRole.FARMER);
        manager.restore(first);
        manager.restore(second);
        VillageAI village = new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), manager, null);
        village.setFoodStock(50);
        village.setBedCount(1);

        assertEquals(AgentRole.BUILDER, new RoleAssignmentService().chooseRoleForAdult(first, village));
    }

    private VillageAI villageWithAgent(AgentRole role) {
        AgentManager manager = new AgentManager(null);
        manager.restore(new Agent(UUID.randomUUID(), role));
        return new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), manager, null);
    }
}
