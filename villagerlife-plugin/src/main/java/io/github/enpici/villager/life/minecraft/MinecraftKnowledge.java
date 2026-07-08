package io.github.enpici.villager.life.minecraft;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MinecraftKnowledge {

    private static final Set<Material> PICKAXES = EnumSet.of(
            Material.WOODEN_PICKAXE,
            Material.STONE_PICKAXE,
            Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE,
            Material.NETHERITE_PICKAXE
    );

    private static final Set<Material> AXES = EnumSet.of(
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.IRON_AXE,
            Material.GOLDEN_AXE,
            Material.DIAMOND_AXE,
            Material.NETHERITE_AXE
    );

    private static final Set<Material> SHOVELS = EnumSet.of(
            Material.WOODEN_SHOVEL,
            Material.STONE_SHOVEL,
            Material.IRON_SHOVEL,
            Material.GOLDEN_SHOVEL,
            Material.DIAMOND_SHOVEL,
            Material.NETHERITE_SHOVEL
    );

    private static final Set<Material> HOES = EnumSet.of(
            Material.WOODEN_HOE,
            Material.STONE_HOE,
            Material.IRON_HOE,
            Material.GOLDEN_HOE,
            Material.DIAMOND_HOE,
            Material.NETHERITE_HOE
    );

    private static final Map<Material, Integer> TOOL_TIERS = Map.ofEntries(
            Map.entry(Material.WOODEN_PICKAXE, 1),
            Map.entry(Material.WOODEN_AXE, 1),
            Map.entry(Material.WOODEN_SHOVEL, 1),
            Map.entry(Material.WOODEN_HOE, 1),
            Map.entry(Material.GOLDEN_PICKAXE, 1),
            Map.entry(Material.GOLDEN_AXE, 1),
            Map.entry(Material.GOLDEN_SHOVEL, 1),
            Map.entry(Material.GOLDEN_HOE, 1),
            Map.entry(Material.STONE_PICKAXE, 2),
            Map.entry(Material.STONE_AXE, 2),
            Map.entry(Material.STONE_SHOVEL, 2),
            Map.entry(Material.STONE_HOE, 2),
            Map.entry(Material.IRON_PICKAXE, 3),
            Map.entry(Material.IRON_AXE, 3),
            Map.entry(Material.IRON_SHOVEL, 3),
            Map.entry(Material.IRON_HOE, 3),
            Map.entry(Material.DIAMOND_PICKAXE, 4),
            Map.entry(Material.DIAMOND_AXE, 4),
            Map.entry(Material.DIAMOND_SHOVEL, 4),
            Map.entry(Material.DIAMOND_HOE, 4),
            Map.entry(Material.NETHERITE_PICKAXE, 5),
            Map.entry(Material.NETHERITE_AXE, 5),
            Map.entry(Material.NETHERITE_SHOVEL, 5),
            Map.entry(Material.NETHERITE_HOE, 5)
    );

    private static final Map<Material, Integer> PICKAXE_BLOCK_TIERS = Map.of(
            Material.STONE, 1,
            Material.COBBLESTONE, 1,
            Material.COAL_ORE, 1,
            Material.IRON_ORE, 2
    );

    public Material sourceBlockFor(Material desired) {
        return switch (desired) {
            case STONE -> Material.STONE;
            case COBBLESTONE -> Material.STONE;
            case COAL -> Material.COAL_ORE;
            case RAW_IRON, IRON_ORE, IRON_INGOT -> Material.IRON_ORE;
            case OAK_PLANKS, STICK, WOODEN_PICKAXE -> Material.OAK_LOG;
            default -> desired;
        };
    }

    public Material requiredToolFor(Material block) {
        Integer tier = PICKAXE_BLOCK_TIERS.get(block);
        if (tier == null) {
            return null;
        }
        return tier <= 1 ? Material.WOODEN_PICKAXE : Material.STONE_PICKAXE;
    }

    public Material preferredToolFor(Material block) {
        if (block == null) {
            return null;
        }
        if (requiredToolFor(block) != null) {
            return requiredToolFor(block);
        }
        String name = block.name();
        if (name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE")
                || name.endsWith("_PLANKS")
                || name.endsWith("_DOOR")
                || name.endsWith("_TRAPDOOR")
                || name.endsWith("_FENCE")
                || name.endsWith("_FENCE_GATE")
                || name.endsWith("_BUTTON")
                || name.endsWith("_PRESSURE_PLATE")
                || name.endsWith("_SIGN")
                || name.endsWith("_CHEST")) {
            return Material.WOODEN_AXE;
        }
        if (block == Material.DIRT
                || block == Material.GRASS_BLOCK
                || block == Material.SAND
                || block == Material.RED_SAND
                || block == Material.GRAVEL
                || block == Material.CLAY
                || block == Material.SOUL_SAND
                || block == Material.SOUL_SOIL
                || block == Material.SNOW_BLOCK
                || block == Material.POWDER_SNOW) {
            return Material.WOODEN_SHOVEL;
        }
        if (name.endsWith("_LEAVES")
                || block == Material.HAY_BLOCK
                || block == Material.MOSS_BLOCK
                || block == Material.SCULK) {
            return Material.WOODEN_HOE;
        }
        return null;
    }

    public boolean isAcceptableTool(Material requiredTool, Material candidate) {
        if (requiredTool == null) {
            return true;
        }
        if (candidate == null || toolFamily(requiredTool) != toolFamily(candidate)) {
            return false;
        }
        int requiredTier = TOOL_TIERS.getOrDefault(requiredTool, Integer.MAX_VALUE);
        int candidateTier = TOOL_TIERS.getOrDefault(candidate, 0);
        return candidateTier >= requiredTier;
    }

    public Material findUsableTool(Map<Material, Integer> stock, Material requiredTool) {
        if (requiredTool == null) {
            return null;
        }
        return stock.keySet().stream()
                .filter(material -> stock.getOrDefault(material, 0) > 0)
                .filter(material -> isAcceptableTool(requiredTool, material))
                .findFirst()
                .orElse(null);
    }

    public boolean canBreakByHand(Material block) {
        return requiredToolFor(block) == null;
    }

    public Material droppedItem(Material block, Material tool, boolean silkTouch) {
        return switch (block) {
            case STONE -> silkTouch ? Material.STONE : Material.COBBLESTONE;
            case COAL_ORE -> silkTouch ? Material.COAL_ORE : Material.COAL;
            case IRON_ORE -> silkTouch ? Material.IRON_ORE : Material.RAW_IRON;
            case OAK_LOG -> Material.OAK_LOG;
            default -> block;
        };
    }

    public Material smeltingOutput(Material input) {
        return switch (input) {
            case COBBLESTONE -> Material.STONE;
            case RAW_IRON, IRON_ORE -> Material.IRON_INGOT;
            default -> null;
        };
    }

    public Material smeltingInputFor(Material output) {
        return switch (output) {
            case STONE -> Material.COBBLESTONE;
            case IRON_INGOT -> Material.RAW_IRON;
            default -> null;
        };
    }

    public boolean needsSmelting(Material current, Material desired) {
        return smeltingOutput(current) == desired;
    }

    public Map<Material, Integer> craftingInputs(Material output) {
        return switch (output) {
            case STICK -> Map.of(Material.OAK_PLANKS, 2);
            case OAK_PLANKS -> Map.of(Material.OAK_LOG, 1);
            case OAK_DOOR -> Map.of(Material.OAK_PLANKS, 6);
            case WOODEN_PICKAXE -> Map.of(Material.OAK_PLANKS, 3, Material.STICK, 2);
            case STONE_PICKAXE -> Map.of(Material.COBBLESTONE, 3, Material.STICK, 2);
            case WOODEN_AXE -> Map.of(Material.OAK_PLANKS, 3, Material.STICK, 2);
            case STONE_AXE -> Map.of(Material.COBBLESTONE, 3, Material.STICK, 2);
            case WOODEN_SHOVEL -> Map.of(Material.OAK_PLANKS, 1, Material.STICK, 2);
            case STONE_SHOVEL -> Map.of(Material.COBBLESTONE, 1, Material.STICK, 2);
            case WOODEN_HOE -> Map.of(Material.OAK_PLANKS, 2, Material.STICK, 2);
            case STONE_HOE -> Map.of(Material.COBBLESTONE, 2, Material.STICK, 2);
            case FURNACE -> Map.of(Material.COBBLESTONE, 8);
            case CRAFTING_TABLE -> Map.of(Material.OAK_PLANKS, 4);
            case TORCH -> Map.of(Material.COAL, 1, Material.STICK, 1);
            default -> Map.of();
        };
    }

    public AcquisitionPlan plan(Material desired, Map<Material, Integer> stock) {
        List<KnowledgeStep> steps = new ArrayList<>();
        Map<Material, Integer> missing = new EnumMap<>(Material.class);
        planInto(desired, Math.max(1, missingAmount(desired, stock, 1)), safeStock(stock), steps, missing, 0);
        return new AcquisitionPlan(desired, List.copyOf(steps), Map.copyOf(missing));
    }

    public List<String> explain(Material desired, Map<Material, Integer> stock) {
        return plan(desired, stock).describeLines();
    }

    private void planInto(Material desired,
                          int amount,
                          Map<Material, Integer> stock,
                          List<KnowledgeStep> steps,
                          Map<Material, Integer> missing,
                          int depth) {
        if (desired == null || amount <= 0 || depth > 8) {
            return;
        }

        int available = stock.getOrDefault(desired, 0);
        if (available >= amount) {
            steps.add(new KnowledgeStep(
                    KnowledgeStep.Kind.USE_STOCK,
                    desired,
                    null,
                    desired,
                    null,
                    null,
                    Map.of(desired, amount),
                    "STORE",
                    "already available"
            ));
            return;
        }

        int needed = amount - available;
        if (available > 0) {
            steps.add(new KnowledgeStep(
                    KnowledgeStep.Kind.USE_STOCK,
                    desired,
                    null,
                    desired,
                    null,
                    null,
                    Map.of(desired, available),
                    "STORE",
                    "partial stock"
            ));
        }

        Material smeltInput = smeltingInputFor(desired);
        if (smeltInput != null) {
            planInto(smeltInput, needed, stock, steps, missing, depth + 1);
            planInto(Material.FURNACE, 1, stock, steps, missing, depth + 1);
            planInto(Material.COAL, needed, stock, steps, missing, depth + 1);
            steps.add(new KnowledgeStep(
                    KnowledgeStep.Kind.SMELT_ITEM,
                    desired,
                    smeltInput,
                    desired,
                    null,
                    Material.FURNACE,
                    Map.of(smeltInput, needed, Material.COAL, needed, Material.FURNACE, 1),
                    "CRAFTER",
                    "uses furnace recipe"
            ));
            return;
        }

        Map<Material, Integer> recipe = craftingInputs(desired);
        if (!recipe.isEmpty()) {
            Material station = craftingStationFor(desired);
            if (station != null) {
                planInto(station, 1, stock, steps, missing, depth + 1);
            }
            recipe.forEach((input, inputAmount) -> planInto(input, inputAmount * needed, stock, steps, missing, depth + 1));
            steps.add(new KnowledgeStep(
                    KnowledgeStep.Kind.CRAFT_ITEM,
                    desired,
                    null,
                    desired,
                    null,
                    station,
                    multiply(recipe, needed),
                    "CRAFTER",
                    "uses crafting recipe"
            ));
            return;
        }

        Material source = sourceBlockFor(desired);
        Material tool = requiredToolFor(source);
        if (tool != null && findUsableTool(stock, tool) == null) {
            planInto(tool, 1, stock, steps, missing, depth + 1);
        }
        Material drop = droppedItem(source, tool, false);
        KnowledgeStep.Kind kind = tool != null ? KnowledgeStep.Kind.MINE_BLOCK : KnowledgeStep.Kind.COLLECT_BLOCK;
        steps.add(new KnowledgeStep(
                kind,
                desired,
                source,
                drop,
                tool,
                null,
                tool != null ? Map.of(tool, 1) : Map.of(),
                tool != null ? "MINER" : "FARMER",
                drop == desired ? "direct source" : "intermediate drop"
        ));
        if (drop != desired && depth <= 7) {
            Map<Material, Integer> withDrop = new HashMap<>(stock);
            withDrop.merge(drop, needed, Integer::sum);
            planInto(desired, needed, withDrop, steps, missing, depth + 1);
        }
        missing.merge(desired, needed, Integer::sum);
    }

    private Material craftingStationFor(Material output) {
        return switch (output) {
            case WOODEN_PICKAXE, STONE_PICKAXE,
                 WOODEN_AXE, STONE_AXE,
                 WOODEN_SHOVEL, STONE_SHOVEL,
                 WOODEN_HOE, STONE_HOE -> Material.CRAFTING_TABLE;
            default -> null;
        };
    }

    private ToolFamily toolFamily(Material tool) {
        if (PICKAXES.contains(tool)) return ToolFamily.PICKAXE;
        if (AXES.contains(tool)) return ToolFamily.AXE;
        if (SHOVELS.contains(tool)) return ToolFamily.SHOVEL;
        if (HOES.contains(tool)) return ToolFamily.HOE;
        return ToolFamily.NONE;
    }

    private enum ToolFamily {
        PICKAXE,
        AXE,
        SHOVEL,
        HOE,
        NONE
    }

    private int missingAmount(Material desired, Map<Material, Integer> stock, int amount) {
        return Math.max(0, amount - safeStock(stock).getOrDefault(desired, 0));
    }

    private Map<Material, Integer> safeStock(Map<Material, Integer> stock) {
        return stock != null ? stock : Map.of();
    }

    private Map<Material, Integer> multiply(Map<Material, Integer> recipe, int amount) {
        Map<Material, Integer> result = new EnumMap<>(Material.class);
        recipe.forEach((material, count) -> result.put(material, count * amount));
        return result;
    }
}
