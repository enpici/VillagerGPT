package io.github.enpici.villager.life.ai;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolRequestTest {

    @Test
    void parsesNestedToolObjectFromPlanJson() {
        String json = """
                {"goal":"GATHER_MATERIALS","priority":72,"skill":"get_stone","tool":{"name":"MINE_BLOCK","material":"STONE","amount":8,"reason":"need cobblestone"},"reason":"upgrade tools"}
                """;

        AgentToolRequest request = AgentToolRequest.parseFromPlanJson(json).orElseThrow();

        assertEquals(AgentTool.MINE_BLOCK, request.tool());
        assertEquals(Material.STONE, request.material());
        assertEquals(8, request.amount());
        assertEquals("mine_block:stonex8", request.compact());
    }

    @Test
    void parsesFlatLegacyToolField() {
        String json = """
                {"goal":"WORK_FOOD","priority":80,"skill":"get_food","tool":"GET_FOOD","reason":"hungry"}
                """;

        AgentToolRequest request = AgentToolRequest.parseFromPlanJson(json).orElseThrow();

        assertEquals(AgentTool.GET_FOOD, request.tool());
        assertTrue(request.args().isEmpty());
    }
}
