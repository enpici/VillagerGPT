package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.AgentGoal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSkillCatalogTest {

    @Test
    void catalogContainsMinimalSurvivalSkillsFromTaxonomy() {
        AgentSkillCatalog catalog = new AgentSkillCatalog();

        assertTrue(catalog.find("get_wood").isPresent());
        assertTrue(catalog.find("make_basic_tools").isPresent());
        assertTrue(catalog.find("get_stone").isPresent());
        assertTrue(catalog.find("build_shelter").isPresent());
        assertTrue(catalog.find("create_food_loop").isPresent());
    }

    @Test
    void skillKeepsSuggestedGoalAndTools() {
        AgentSkill getStone = new AgentSkillCatalog().find("get_stone").orElseThrow();

        assertEquals(AgentGoal.GATHER_MATERIALS, getStone.suggestedGoal());
        assertTrue(getStone.tools().contains(AgentTool.MINE_BLOCK));
        assertTrue(getStone.summary().contains("cobblestone"));
    }
}
