package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.AgentGoal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class AgentSkillCatalog {

    private final List<AgentSkill> skills;

    public AgentSkillCatalog() {
        this.skills = List.copyOf(defaultSkills());
    }

    public List<AgentSkill> all() {
        return skills;
    }

    public Optional<AgentSkill> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(id);
        return skills.stream()
                .filter(skill -> normalize(skill.id()).equals(normalized))
                .findFirst();
    }

    public List<AgentSkill> forGoal(AgentGoal goal, int limit) {
        int effectiveLimit = Math.max(1, limit);
        return skills.stream()
                .filter(skill -> goal == null || skill.suggestedGoal() == goal || skill.suggestedGoal() == AgentGoal.IDLE)
                .sorted(Comparator.comparingInt(AgentSkill::level).thenComparing(AgentSkill::id))
                .limit(effectiveLimit)
                .toList();
    }

    public String promptSummary(AgentGoal currentGoal) {
        List<AgentSkill> selected = new ArrayList<>();
        selected.addAll(forGoal(currentGoal, 8));
        skills.stream()
                .filter(skill -> skill.level() <= 4)
                .filter(skill -> selected.stream().noneMatch(existing -> existing.id().equals(skill.id())))
                .limit(12)
                .forEach(selected::add);
        return selected.stream()
                .sorted(Comparator.comparingInt(AgentSkill::level).thenComparing(AgentSkill::id))
                .map(AgentSkill::compactPromptLine)
                .collect(Collectors.joining("; "));
    }

    public String toolSchema() {
        return """
                Return an optional tool object when a concrete next action is useful:
                "tool":{"name":"CRAFT_ITEM|MINE_BLOCK|GET_FOOD|BUILD_SHELTER|EAT_FOOD|FLEE_FROM_MOB|SMELT_ITEM|PICKUP_ITEMS|DEPOSIT_TO_CHEST|PLACE_BLOCK","material":"MATERIAL_OR_BLOCK","amount":1,"target":"short target","reason":"why this action now"}
                Tool rules: tools are requests, not magic. The server validates inventory, blocks, danger, recipes, furnaces and chests. If a tool fails, choose a recovery skill next time.
                """;
    }

    private List<AgentSkill> defaultSkills() {
        return List.of(
                skill("look_around", 0, AgentGoal.IDLE, "scan blocks, items, mobs, sky and danger before choosing action",
                        List.of("agent alive"), List.of("rotate perception", "detect nearby blocks/items/mobs"),
                        List.of("environment facts are known"), List.of("if nothing useful is visible, explore nearby"), AgentTool.LOOK_AROUND),
                skill("move_to", 0, AgentGoal.IDLE, "move toward a coordinate, block, item or entity",
                        List.of("target known"), List.of("path toward target", "avoid obstacles", "stop within interaction range"),
                        List.of("distance to target is low"), List.of("if stuck, choose another nearby target"), AgentTool.MOVE_TO),
                skill("avoid_hazard", 0, AgentGoal.SEEK_SAFETY, "avoid lava, falls, drowning, mobs and unsafe darkness",
                        List.of("hazard detected"), List.of("increase distance", "prefer lit/solid ground", "return to base if possible"),
                        List.of("hazard distance is safe"), List.of("build temporary barrier or flee"), AgentTool.AVOID_HAZARD, AgentTool.FLEE_FROM_MOB),
                skill("break_block", 0, AgentGoal.GATHER_MATERIALS, "break a reachable block using the best available tool or hand if allowed",
                        List.of("block reachable"), List.of("equip preferred tool", "break block", "collect drop"),
                        List.of("block removed and drop collected"), List.of("craft needed tool or choose hand-breakable block"), AgentTool.BREAK_BLOCK, AgentTool.PICKUP_ITEMS),
                skill("place_block", 0, AgentGoal.BUILD_HOUSING, "place a carried block on a valid face for shelter or structure work",
                        List.of("has placeable block"), List.of("select block", "target face", "place block"),
                        List.of("block exists at target"), List.of("move to another face or choose another block"), AgentTool.PLACE_BLOCK),
                skill("pickup_items", 0, AgentGoal.GATHER_MATERIALS, "walk over dropped items and synchronize physical inventory",
                        List.of("item entity nearby"), List.of("move to item", "collect", "verify inventory"),
                        List.of("item appears in inventory or chest stock"), List.of("if inventory full, deposit first"), AgentTool.PICKUP_ITEMS),
                skill("craft_item", 1, AgentGoal.CRAFT_SUPPLIES, "craft an item only when real recipe inputs are available",
                        List.of("recipe known", "inputs available"), List.of("withdraw inputs", "use inventory or crafting table", "create output"),
                        List.of("output item exists"), List.of("request missing inputs"), AgentTool.CRAFT_ITEM),
                skill("equip_item", 1, AgentGoal.GATHER_MATERIALS, "equip tool or weapon best suited to the next block or mob",
                        List.of("item exists"), List.of("select best family and tier", "put in main hand"),
                        List.of("main hand matches action"), List.of("craft or request missing equipment"), AgentTool.EQUIP_ITEM),
                skill("eat_food", 1, AgentGoal.SURVIVE_HUNGER, "eat available food before starvation interrupts work",
                        List.of("hunger high", "food available"), List.of("select food", "eat until safe"),
                        List.of("hunger need reduced"), List.of("get_food if no food exists"), AgentTool.EAT_FOOD),
                skill("flee_from_mob", 1, AgentGoal.SEEK_SAFETY, "increase distance from hostile mobs when gear or health is unsafe",
                        List.of("hostile mob near"), List.of("move away", "prefer shelter or lit village center"),
                        List.of("mob is outside danger range"), List.of("build shelter or call guard"), AgentTool.FLEE_FROM_MOB),
                skill("attack_mob", 1, AgentGoal.PATROL, "fight only when equipment, health and distance make it reasonable",
                        List.of("mob hostile", "agent can survive"), List.of("equip weapon", "attack", "retreat between hits"),
                        List.of("mob killed or driven away"), List.of("flee if health or gear is bad"), AgentTool.ATTACK_MOB),
                skill("get_wood", 2, AgentGoal.GATHER_MATERIALS, "obtain logs from trees; hand is allowed, axe is faster",
                        List.of("tree visible or exploration possible"), List.of("find trunk", "move to trunk", "break logs", "pick up drops"),
                        List.of("inventory or stock has enough logs"), List.of("explore in spiral if no tree found"), AgentTool.MINE_BLOCK, AgentTool.PICKUP_ITEMS),
                skill("make_crafting_table", 3, AgentGoal.CRAFT_SUPPLIES, "convert logs to planks and craft/place a crafting table",
                        List.of("at least one log"), List.of("craft planks", "craft crafting table", "place or store table"),
                        List.of("crafting table available"), List.of("get_wood first"), AgentTool.CRAFT_ITEM, AgentTool.PLACE_BLOCK),
                skill("make_basic_tools", 3, AgentGoal.CRAFT_SUPPLIES, "craft sticks, wooden pickaxe, axe, shovel or hoe for the next resource",
                        List.of("planks and sticks available or craftable"), List.of("craft sticks", "craft required tool family", "equip it"),
                        List.of("needed tool exists"), List.of("get_wood or make_crafting_table first"), AgentTool.CRAFT_ITEM, AgentTool.EQUIP_ITEM),
                skill("get_stone", 3, AgentGoal.GATHER_MATERIALS, "mine stone with pickaxe; stone drops cobblestone unless silk touch, smelt cobblestone back to stone",
                        List.of("wooden pickaxe or better"), List.of("find stone", "equip pickaxe", "mine stone", "collect cobblestone"),
                        List.of("cobblestone stock is high enough"), List.of("craft pickaxe or gather wood first"), AgentTool.MINE_BLOCK),
                skill("make_furnace", 3, AgentGoal.CRAFT_SUPPLIES, "craft furnace from eight cobblestone for cooking and smelting",
                        List.of("cobblestone >= 8"), List.of("craft furnace", "place or store furnace"),
                        List.of("furnace available"), List.of("get_stone first"), AgentTool.CRAFT_ITEM, AgentTool.PLACE_BLOCK),
                skill("make_torches", 4, AgentGoal.CRAFT_SUPPLIES, "create torches from coal or charcoal plus sticks",
                        List.of("coal or charcoal", "sticks"), List.of("craft torches", "place in shelter/mines when needed"),
                        List.of("torches available"), List.of("make charcoal with furnace if coal missing"), AgentTool.CRAFT_ITEM, AgentTool.SMELT_ITEM),
                skill("get_food", 4, AgentGoal.WORK_FOOD, "solve hunger via crops, animals, apples, berries or village food",
                        List.of("hunger or village food need"), List.of("find food source", "harvest/hunt", "cook if useful", "eat or store"),
                        List.of("food stock or hunger is safe"), List.of("create food loop if no immediate food exists"), AgentTool.GET_FOOD, AgentTool.EAT_FOOD),
                skill("build_shelter", 5, AgentGoal.BUILD_SHELTER, "build a closed lit 3x3 or 4x4 shelter when night or monsters threaten",
                        List.of("blocks available or mineable"), List.of("choose flat area", "build walls", "leave entrance", "add roof", "light if possible"),
                        List.of("agent is enclosed and safer from mobs"), List.of("dig into hill or use dirt if materials are low"), AgentTool.BUILD_SHELTER, AgentTool.PLACE_BLOCK),
                skill("sleep_or_wait_safe", 5, AgentGoal.REST, "use a bed at night or wait inside shelter if no bed exists",
                        List.of("night or low energy"), List.of("find bed", "sleep if possible", "otherwise stay sheltered"),
                        List.of("daytime or energy need reduced"), List.of("make bed or build shelter"), AgentTool.SLEEP_OR_WAIT_SAFE),
                skill("sort_inventory", 5, AgentGoal.CRAFT_SUPPLIES, "keep tools, food and blocks useful; deposit overflow in chests",
                        List.of("chest exists or inventory has extras"), List.of("keep hotbar useful", "deposit surplus", "withdraw needed inputs"),
                        List.of("items are available for future tasks"), List.of("craft chest if storage is missing"), AgentTool.DEPOSIT_TO_CHEST, AgentTool.WITHDRAW_FROM_CHEST),
                skill("create_food_loop", 6, AgentGoal.WORK_FOOD, "make sustainable food with seeds, hoe, water, crops or animals",
                        List.of("seeds/animals or searchable area"), List.of("get seeds", "craft hoe", "till soil near water", "plant", "harvest and replant"),
                        List.of("food production can repeat"), List.of("hunt or gather apples until farm grows"), AgentTool.GET_FOOD, AgentTool.CRAFT_ITEM, AgentTool.PLACE_BLOCK),
                skill("mine_safe", 8, AgentGoal.GATHER_MATERIALS, "mine useful resources while avoiding vertical digging, mobs, lava and hunger",
                        List.of("pickaxe", "food", "safe route"), List.of("enter cave or stair mine", "place torches", "mine target", "return before danger"),
                        List.of("target resource collected and agent returns"), List.of("retreat, craft tool, eat, or deposit inventory"), AgentTool.MINE_BLOCK, AgentTool.FLEE_FROM_MOB),
                skill("establish_base", 5, AgentGoal.BUILD_HOUSING, "centralize shelter, chest, furnace, crafting table, bed and known coordinates",
                        List.of("safe location"), List.of("build shelter", "place utilities", "mark storage", "organize zones"),
                        List.of("base has storage and core utilities"), List.of("build emergency shelter first"), AgentTool.BUILD_SHELTER, AgentTool.PLACE_BLOCK, AgentTool.DEPOSIT_TO_CHEST)
        );
    }

    private AgentSkill skill(String id,
                             int level,
                             AgentGoal goal,
                             String summary,
                             List<String> preconditions,
                             List<String> steps,
                             List<String> success,
                             List<String> recovery,
                             AgentTool... tools) {
        return new AgentSkill(id, level, goal, summary, preconditions, steps, success, recovery, List.of(tools));
    }

    private String normalize(String value) {
        return value.trim().replace('-', '_').replace(' ', '_').toLowerCase(Locale.ROOT);
    }
}
