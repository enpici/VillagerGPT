package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentGoal;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolExecutorTest {

    @Test
    void mineStoneToolRequestsCobblestoneLikeVanillaDrop() {
        VillageAI village = villageWith(new Agent(UUID.randomUUID(), AgentRole.MINER));
        AgentToolRequest request = new AgentToolRequest(AgentTool.MINE_BLOCK, Material.STONE, 8, "", "need stone chain");

        AgentToolExecutor.ToolExecution execution = new AgentToolExecutor().apply(village, request);

        assertTrue(execution.accepted());
        assertEquals(AgentGoal.GATHER_MATERIALS, execution.impliedGoal());
        VillageAI.MaterialRequest materialRequest = village.pollMaterialRequest();
        assertNotNull(materialRequest);
        assertEquals(Material.COBBLESTONE, materialRequest.material());
        assertEquals(8, materialRequest.amount());
    }

    @Test
    void craftItemToolRequestsPhysicalOutputThroughCraftingGoal() {
        VillageAI village = villageWith(new Agent(UUID.randomUUID(), AgentRole.CRAFTER));
        AgentToolRequest request = new AgentToolRequest(AgentTool.CRAFT_ITEM, Material.WOODEN_AXE, 1, "", "logs need axe");

        AgentToolExecutor.ToolExecution execution = new AgentToolExecutor().apply(village, request);

        assertTrue(execution.accepted());
        assertEquals(AgentGoal.CRAFT_SUPPLIES, execution.impliedGoal());
        assertEquals(Material.WOODEN_AXE, village.pollMaterialRequest().material());
    }

    @Test
    void mineToolWithoutMaterialIsRejectedInsteadOfInventingLogs() {
        VillageAI village = villageWith(new Agent(UUID.randomUUID(), AgentRole.MINER));
        AgentToolRequest request = new AgentToolRequest(AgentTool.MINE_BLOCK, null, 1, "nearby resource", "ambiguous");

        AgentToolExecutor.ToolExecution execution = new AgentToolExecutor().apply(village, request);

        assertTrue(!execution.accepted());
        assertEquals(null, village.pollMaterialRequest());
    }

    private VillageAI villageWith(Agent agent) {
        AgentManager manager = new AgentManager(null);
        manager.restore(agent);
        return new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), manager, null);
    }
}
