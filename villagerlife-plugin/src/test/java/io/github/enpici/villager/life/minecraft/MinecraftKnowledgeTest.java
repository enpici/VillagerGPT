package io.github.enpici.villager.life.minecraft;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftKnowledgeTest {

    private final MinecraftKnowledge knowledge = new MinecraftKnowledge();

    @Test
    void stonePlanIncludesPickaxeCobblestoneAndSmelting() {
        AcquisitionPlan plan = knowledge.plan(Material.STONE, Map.of());
        String text = plan.describeLines().stream().collect(Collectors.joining("\n"));

        assertTrue(text.contains("mine stone with wooden_pickaxe -> cobblestone"));
        assertTrue(text.contains("smelt cobblestone in furnace -> stone"));
        assertTrue(text.contains("craft wooden_pickaxe"));
    }

    @Test
    void ironOreRequiresStoneTierPickaxe() {
        assertEquals(Material.STONE_PICKAXE, knowledge.requiredToolFor(Material.IRON_ORE));
        assertTrue(knowledge.isAcceptableTool(Material.STONE_PICKAXE, Material.IRON_PICKAXE));
    }

    @Test
    void preferredToolsMatchBlockFamilies() {
        assertEquals(Material.WOODEN_AXE, knowledge.preferredToolFor(Material.OAK_LOG));
        assertEquals(Material.WOODEN_SHOVEL, knowledge.preferredToolFor(Material.DIRT));
        assertEquals(Material.WOODEN_HOE, knowledge.preferredToolFor(Material.OAK_LEAVES));
        assertEquals(Material.WOODEN_PICKAXE, knowledge.preferredToolFor(Material.STONE));
    }

    @Test
    void toolUpgradesStayInsideTheSameFamily() {
        assertTrue(knowledge.isAcceptableTool(Material.WOODEN_AXE, Material.IRON_AXE));
        assertTrue(knowledge.isAcceptableTool(Material.WOODEN_SHOVEL, Material.DIAMOND_SHOVEL));
        assertTrue(!knowledge.isAcceptableTool(Material.WOODEN_AXE, Material.IRON_PICKAXE));
    }

    @Test
    void silkTouchChangesStoneDropRule() {
        assertEquals(Material.COBBLESTONE, knowledge.droppedItem(Material.STONE, Material.WOODEN_PICKAXE, false));
        assertEquals(Material.STONE, knowledge.droppedItem(Material.STONE, Material.WOODEN_PICKAXE, true));
    }

    @Test
    void craftingRecipesDescribePlayerPrerequisites() {
        assertEquals(Map.of(Material.OAK_PLANKS, 3, Material.STICK, 2), knowledge.craftingInputs(Material.WOODEN_PICKAXE));
        assertEquals(Map.of(Material.OAK_PLANKS, 3, Material.STICK, 2), knowledge.craftingInputs(Material.WOODEN_AXE));
        assertEquals(Map.of(Material.OAK_PLANKS, 1, Material.STICK, 2), knowledge.craftingInputs(Material.WOODEN_SHOVEL));
        assertEquals(Map.of(Material.COBBLESTONE, 8), knowledge.craftingInputs(Material.FURNACE));
        assertEquals(Map.of(Material.COAL, 1, Material.STICK, 1), knowledge.craftingInputs(Material.TORCH));
    }
}
