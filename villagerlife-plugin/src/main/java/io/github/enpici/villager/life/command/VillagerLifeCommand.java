package io.github.enpici.villager.life.command;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.agent.LifeStage;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.event.AgentRoleChangedEvent;
import io.github.enpici.villager.life.minecraft.MinecraftKnowledge;
import io.github.enpici.villager.life.observability.SimulationJournal;
import io.github.enpici.villager.life.persistence.LifeRepository;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.VillagerLifePlugin;
import io.github.enpici.villager.life.village.VillageAI;
import io.github.enpici.villager.life.village.BedLocator;
import io.github.enpici.villager.life.village.PhysicalResourceScanner;
import io.github.enpici.villager.life.village.VillageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class VillagerLifeCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AgentManager agentManager;
    private final VillageManager villageManager;
    private final BlueprintService blueprintService;
    private final SimulationJournal simulationJournal;
    private final LifeRepository lifeRepository;
    private final MinecraftKnowledge minecraftKnowledge = new MinecraftKnowledge();
    private final BedLocator bedLocator = new BedLocator();
    private final PhysicalResourceScanner physicalResourceScanner = new PhysicalResourceScanner();

    public VillagerLifeCommand(AgentManager agentManager,
                               VillageManager villageManager,
                               BlueprintService blueprintService,
                               SimulationJournal simulationJournal,
                               LifeRepository lifeRepository) {
        this.agentManager = agentManager;
        this.villageManager = villageManager;
        this.blueprintService = blueprintService;
        this.simulationJournal = simulationJournal;
        this.lifeRepository = lifeRepository;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("/villagerlife <createvillage|register|debug|setrole|build|status|stock|roles|goals|knowledge|family|buildstatus|journal|beds|prune>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "createvillage" -> handleCreateVillage(sender);
            case "register" -> handleRegister(sender);
            case "debug" -> handleDebug(sender, args);
            case "setrole" -> handleSetRole(sender, args);
            case "build" -> handleBuild(sender, args);
            case "status" -> handleStatus(sender);
            case "stock" -> handleStock(sender, args);
            case "roles" -> handleRoles(sender);
            case "goals" -> handleGoals(sender);
            case "knowledge" -> handleKnowledge(sender, args);
            case "family" -> handleFamily(sender, args);
            case "buildstatus" -> handleBuildStatus(sender, args);
            case "journal" -> handleJournal(sender, args);
            case "beds" -> handleBeds(sender, args);
            case "prune" -> handlePrune(sender);
            default -> sender.sendMessage("Subcomando desconocido: " + sub);
        }
        return true;
    }

    private void handleCreateVillage(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return;
        }
        cleanupManagedAgentsNear(player.getLocation(), 48.0);
        villageManager.createVillage("aldea_principal", player.getLocation(), agentManager, blueprintService);
        var village = villageManager.currentVillage().orElse(null);
        List<AgentRole> initialRoles = initialRoles();
        if (village != null && VillagerLifePlugin.instance() != null) {
            var config = VillagerLifePlugin.instance().getConfig();
            village.setMaxPopulation(config.getInt("village.max-population", 30));
            village.setPopulationTarget(Math.max(config.getInt("village.initial-target-population", 8), initialRoles.size()));
            village.setFoodStock(config.getInt("village.initial-food-stock", 20));
            int starterBeds = Math.max(config.getInt("village.initial-bed-count", 8), initialRoles.size());
            List<Location> placedBeds = bedLocator.placeStarterBeds(player.getLocation(), starterBeds);
            Location storage = placeStarterStorage(player.getLocation(), village);
            int physicalBeds = syncLocatedBeds(village);
            if (simulationJournal != null) {
                simulationJournal.record(
                        "beds_placed",
                        village,
                        null,
                        "requested=" + starterBeds + " placed=" + placedBeds.size()
                                + " physicalBeds=" + physicalBeds
                                + " locations=" + formatLocations(placedBeds)
                );
                if (storage != null) {
                    simulationJournal.record("storage_placed", village, null, "location=" + formatLocation(storage));
                }
            }
        }
        List<Agent> createdAgents = spawnInitialAgents(player.getLocation(), initialRoles);
        if (simulationJournal != null) {
            simulationJournal.record("village_created", village, null, "created_by=" + player.getName() + " initialRoles=" + initialRoles);
            for (Agent agent : createdAgents) {
                simulationJournal.record("agent_registered", village, agent, "role=" + agent.role());
            }
        }
        saveNow();
        sender.sendMessage("VillageAI creada en tu posición con " + createdAgents.size() + " agentes iniciales: " + initialRoles);
    }

    private List<AgentRole> initialRoles() {
        VillagerLifePlugin plugin = VillagerLifePlugin.instance();
        List<String> configured = plugin != null ? plugin.getConfig().getStringList("village.initial-roles") : List.of();
        List<AgentRole> roles = configured.stream()
                .map(value -> {
                    try {
                        return AgentRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException exception) {
                        return null;
                    }
                })
                .filter(role -> role != null)
                .toList();
        if (!roles.isEmpty()) {
            return roles;
        }
        return List.of(AgentRole.FARMER, AgentRole.BUILDER, AgentRole.MINER, AgentRole.CRAFTER, AgentRole.GUARD, AgentRole.TRADER, AgentRole.LEADER);
    }

    private List<Agent> spawnInitialAgents(Location center, List<AgentRole> roles) {
        if (center == null || center.getWorld() == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Agent> created = new ArrayList<>();
        double radius = Math.max(2.5, roles.size() * 0.45);
        for (int index = 0; index < roles.size(); index++) {
            AgentRole role = roles.get(index);
            double angle = (Math.PI * 2.0 * index) / roles.size();
            Location spawn = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            spawn.setY(center.getWorld().getHighestBlockYAt(spawn) + 1);
            Villager villager = center.getWorld().spawn(spawn, Villager.class);
            setupAgentVillager(villager, displayNameFor(role));
            setProfessionForRole(villager, role);
            created.add(agentManager.register(villager, role));
        }
        return created;
    }

    private String displayNameFor(AgentRole role) {
        return switch (role) {
            case FARMER -> "Agente granjero";
            case BUILDER -> "Agente constructor";
            case MINER -> "Agente minero";
            case CRAFTER -> "Agente artesano";
            case GUARD -> "Agente guardia";
            case TRADER -> "Agente comerciante";
            case LEADER -> "Agente lider";
        };
    }

    private void setProfessionForRole(Villager villager, AgentRole role) {
        Villager.Profession profession = switch (role) {
            case FARMER -> Villager.Profession.FARMER;
            case BUILDER -> Villager.Profession.MASON;
            case MINER -> Villager.Profession.TOOLSMITH;
            case CRAFTER -> Villager.Profession.CARTOGRAPHER;
            case GUARD -> Villager.Profession.WEAPONSMITH;
            case TRADER -> Villager.Profession.LIBRARIAN;
            case LEADER -> Villager.Profession.CLERIC;
        };
        villager.setProfession(profession);
    }

    private Location placeStarterStorage(Location origin, VillageAI village) {
        if (origin == null || origin.getWorld() == null) {
            return null;
        }
        Location location = origin.clone().add(0, 0, 3);
        location.setY(origin.getWorld().getHighestBlockYAt(location) + 1);
        if (!location.getBlock().isEmpty()) {
            location = origin.clone().add(2, 0, 3);
            location.setY(origin.getWorld().getHighestBlockYAt(location) + 1);
        }
        location.getBlock().setType(Material.CHEST, true);
        if (location.getBlock().getState() instanceof Container container) {
            int foodPoints = Math.max(0, village.foodStock());
            int bread = Math.max(1, (int) Math.ceil(foodPoints / 3.0));
            container.getInventory().addItem(new ItemStack(Material.BREAD, bread));
            container.getInventory().addItem(new ItemStack(Material.OAK_LOG, 4));
            village.addMaterialStock(Material.BREAD, bread);
            village.addMaterialStock(Material.OAK_LOG, 4);
        }
        return location;
    }

    private void cleanupManagedAgentsNear(Location center, double radius) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        double radiusSquared = radius * radius;
        for (Agent agent : new ArrayList<>(agentManager.all())) {
            Villager villager;
            try {
                villager = agent.resolveVillager();
            } catch (IllegalStateException | NullPointerException exception) {
                agentManager.unregister(agent.villagerUuid());
                continue;
            }
            if (villager == null || !villager.isValid()) {
                agentManager.unregister(agent.villagerUuid());
                continue;
            }
            if (villager.getWorld() == center.getWorld() && villager.getLocation().distanceSquared(center) <= radiusSquared) {
                villager.remove();
                agentManager.unregister(agent.villagerUuid());
            }
        }
    }

    private void setupAgentVillager(Villager villager, String name) {
        villager.setCustomName(name);
        villager.setCustomNameVisible(true);
        villager.setRemoveWhenFarAway(false);
        villager.setCanPickupItems(true);
    }

    private void handleRegister(CommandSender sender) {
        Villager villager = nearestVillager(sender);
        if (villager == null) {
            sender.sendMessage("No hay aldeano cerca.");
            return;
        }
        Agent agent = agentManager.register(villager, AgentRole.FARMER);
        if (simulationJournal != null) {
            simulationJournal.record("agent_registered", villageManager.currentVillage().orElse(null), agent, "role=FARMER source=command");
        }
        sender.sendMessage("Aldeano registrado: " + agent.villagerUuid());
    }

    private void handleDebug(CommandSender sender, String[] args) {
        Agent agent = resolveAgent(sender, args.length > 1 ? args[1] : null);
        if (agent == null) {
            sender.sendMessage("Agente no encontrado.");
            return;
        }
        sender.sendMessage("Agent " + agent.villagerUuid() + " role=" + agent.role() + " task=" +
                (agent.activeTask() != null ? agent.activeTask().id() : "none")
                + " stage=" + agent.lifeStage()
                + " ageTicks=" + agent.ageTicks()
                + " generation=" + agent.generation()
                + " partner=" + agent.partner()
                + " parents=" + agent.parentA() + "," + agent.parentB()
                + " goal=" + agent.currentGoal()
                + " goalPriority=" + agent.currentGoalPriority()
                + " goalReason='" + agent.currentGoalReason() + "'"
                + " decision='" + agent.lastDecisionReason() + "'"
                + " needs=" + agent.needsSnapshot());
    }

    private void handleSetRole(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Uso: /villagerlife setrole <uuid> <role>");
            return;
        }
        Agent agent = resolveAgent(sender, args[1]);
        if (agent == null) {
            sender.sendMessage("Agente no encontrado.");
            return;
        }
        try {
            AgentRole newRole = AgentRole.valueOf(args[2].toUpperCase(Locale.ROOT));
            AgentRole previous = agent.role();
            agent.setRole(newRole);
            org.bukkit.Bukkit.getPluginManager().callEvent(new AgentRoleChangedEvent(agent, previous, newRole));
            if (simulationJournal != null) {
                simulationJournal.record("role_changed", villageManager.currentVillage().orElse(null), agent, "from=" + previous + " to=" + newRole + " source=command");
            }
            sender.sendMessage("Rol actualizado a " + newRole);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("Rol inválido. Usa: " + Arrays.toString(AgentRole.values()));
        }
    }

    private void handleBuild(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Uso: /villagerlife build <blueprint>");
            return;
        }
        var village = villageManager.currentVillage().orElse(null);
        if (village == null) {
            sender.sendMessage("No hay VillageAI activa.");
            return;
        }
        village.enqueueBlueprint(args[1]);
        if (simulationJournal != null) {
            simulationJournal.record("build_requested", village, null, "blueprint=" + args[1] + " source=command");
        }
        sender.sendMessage("Blueprint encolado: " + args[1]);
    }

    private void handleStatus(CommandSender sender) {
        var village = villageManager.currentVillage().orElse(null);
        if (village == null) {
            sender.sendMessage("No hay aldea activa.");
            return;
        }
        Map<LifeStage, Long> stages = agentManager.all().stream()
                .collect(Collectors.groupingBy(Agent::lifeStage, Collectors.counting()));
        int physicalBeds = syncLocatedBeds(village);
        long missing = agentManager.all().stream().filter(Agent::missingEntity).count();
        long physical = agentManager.size() - missing;
        sender.sendMessage("Village " + village.name() + " pop=" + village.population() + " food=" + village.foodStock() +
                " beds=" + village.bedCount()
                + " physicalBeds=" + physicalBeds
                + " stock=" + village.materialStockSnapshot()
                + " reserved=" + village.reservedMaterialsSnapshot()
                + " agents=" + agentManager.size()
                + " physical=" + physical
                + " missing=" + missing
                + " adults=" + stages.getOrDefault(LifeStage.ADULT, 0L)
                + " children=" + stages.getOrDefault(LifeStage.CHILD, 0L));
    }

    private void handleStock(CommandSender sender, String[] args) {
        var village = villageManager.currentVillage().orElse(null);
        if (village == null) {
            sender.sendMessage("No hay aldea activa.");
            return;
        }
        int radius = resourceScanRadius();
        if (args.length > 1) {
            try {
                radius = Math.max(1, Math.min(128, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
            }
        }
        var snapshot = physicalResourceScanner.scan(village, agentManager.all(), radius);
        Map<Material, Integer> previous = village.materialStockSnapshot();
        int previousFood = village.foodStock();
        village.replaceMaterialStock(snapshot.materials());
        village.setFoodStock(snapshot.foodPoints());
        saveNow();
        if (simulationJournal != null) {
            simulationJournal.record(
                    "resources_synced",
                    village,
                    null,
                    "phase=command radius=" + radius
                            + " containers=" + snapshot.containerCount()
                            + " agentInventories=" + snapshot.agentInventoryCount()
                            + " food=" + previousFood + "->" + snapshot.foodPoints()
                            + " stock=" + previous + "->" + village.materialStockSnapshot()
            );
        }
        sender.sendMessage("Stock fisico sincronizado radio=" + radius
                + " containers=" + snapshot.containerCount()
                + " agentInventories=" + snapshot.agentInventoryCount()
                + " food=" + previousFood + "->" + village.foodStock()
                + " stock=" + village.materialStockSnapshot());
    }

    private void handleRoles(CommandSender sender) {
        Map<AgentRole, Long> roles = agentManager.all().stream()
                .collect(Collectors.groupingBy(Agent::role, Collectors.counting()));
        String summary = Arrays.stream(AgentRole.values())
                .map(role -> role.name() + "=" + roles.getOrDefault(role, 0L) + " (" + role.purpose() + ")")
                .collect(Collectors.joining(", "));
        sender.sendMessage("Roles: " + summary);
    }

    private void handleGoals(CommandSender sender) {
        if (agentManager.all().isEmpty()) {
            sender.sendMessage("No hay agentes registrados.");
            return;
        }

        agentManager.all().stream()
                .sorted(java.util.Comparator.comparing(agent -> agent.currentGoal().name()))
                .forEach(agent -> sender.sendMessage("Goal " + agent.currentGoal()
                        + " priority=" + agent.currentGoalPriority()
                        + " role=" + agent.role()
                        + " uuid=" + agent.villagerUuid()
                        + " reason='" + agent.currentGoalReason() + "'"
                        + " task=" + (agent.activeTask() != null ? agent.activeTask().id() : "none")));
    }

    private void handleKnowledge(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Uso: /villagerlife knowledge <material>");
            return;
        }
        Material material = Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
        if (material == null) {
            sender.sendMessage("Material desconocido: " + args[1]);
            return;
        }

        VillageAI village = villageManager.currentVillage().orElse(null);
        Map<Material, Integer> stock = village != null ? village.materialStockSnapshot() : Map.of();
        var plan = minecraftKnowledge.plan(material, stock);
        sender.sendMessage("Knowledge plan for " + material.name().toLowerCase() + ":");
        plan.describeLines().stream().limit(12).forEach(line -> sender.sendMessage("- " + line));
        if (plan.describeLines().size() > 12) {
            sender.sendMessage("- ...");
        }
    }

    private void handleJournal(CommandSender sender, String[] args) {
        if (simulationJournal == null) {
            sender.sendMessage("Journal no disponible.");
            return;
        }
        int limit = 12;
        if (args.length > 1) {
            try {
                limit = Math.max(1, Math.min(40, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
            }
        }
        sender.sendMessage("[Journal] Ultimas " + limit + " entradas. Archivo: " + simulationJournal.logFile().getPath());
        for (String line : simulationJournal.recent(limit)) {
            sender.sendMessage("- " + line);
        }
    }

    private void handleBeds(CommandSender sender, String[] args) {
        VillageAI village = villageManager.currentVillage().orElse(null);
        Location center;
        if (village != null) {
            center = village.center();
        } else if (sender instanceof Player player) {
            center = player.getLocation();
        } else {
            sender.sendMessage("No hay aldea activa ni jugador para usar como centro.");
            return;
        }

        int radius = bedScanRadius();
        if (args.length > 1) {
            try {
                radius = Math.max(1, Math.min(128, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
            }
        }

        List<Location> beds = bedLocator.findBedHeads(center, radius);
        if (village != null) {
            village.setBedCount(beds.size());
            saveNow();
        }
        if (simulationJournal != null) {
            simulationJournal.record(
                    "beds_scanned",
                    village,
                    null,
                    "radius=" + radius + " physicalBeds=" + beds.size() + " locations=" + formatLocations(beds)
            );
        }
        sender.sendMessage("Camas fisicas encontradas=" + beds.size() + " radio=" + radius);
        beds.stream()
                .limit(12)
                .forEach(location -> sender.sendMessage("- " + bedLocator.format(location)));
        if (beds.size() > 12) {
            sender.sendMessage("- ... +" + (beds.size() - 12));
        }
    }

    private void handlePrune(CommandSender sender) {
        int removed = 0;
        VillageAI village = villageManager.currentVillage().orElse(null);
        for (Agent agent : new ArrayList<>(agentManager.all())) {
            Villager villager;
            try {
                villager = agent.resolveVillager();
            } catch (IllegalStateException | NullPointerException exception) {
                villager = null;
            }
            if (villager != null && villager.isValid()) {
                continue;
            }
            if (simulationJournal != null) {
                simulationJournal.record("agent_pruned", village, agent, "source=command reason=entity_not_found");
            }
            if (agentManager.unregister(agent.villagerUuid())) {
                removed++;
            }
        }
        saveNow();
        sender.sendMessage("Agentes huerfanos eliminados: " + removed);
    }

    private void handleFamily(CommandSender sender, String[] args) {
        Agent agent = resolveAgent(sender, args.length > 1 ? args[1] : null);
        if (agent == null) {
            sender.sendMessage("Agente no encontrado.");
            return;
        }
        String children = agentManager.all().stream()
                .filter(candidate -> agent.villagerUuid().equals(candidate.parentA()) || agent.villagerUuid().equals(candidate.parentB()))
                .map(candidate -> candidate.villagerUuid().toString())
                .collect(Collectors.joining(", "));
        sender.sendMessage("Family " + agent.villagerUuid()
                + " generation=" + agent.generation()
                + " partner=" + agent.partner()
                + " parents=" + agent.parentA() + "," + agent.parentB()
                + " children=" + (children.isBlank() ? "none" : children));
    }

    private void handleBuildStatus(CommandSender sender, String[] args) {
        if (args.length > 1 && "reset".equalsIgnoreCase(args[1])) {
            blueprintService.telemetry().resetCountersAndErrors();
            sender.sendMessage("Build telemetry reseteada (éxitos/fallos/reintentos/errores recientes).");
            return;
        }

        VillageAI village = villageManager.currentVillage().orElse(null);
        var counters = blueprintService.telemetry().snapshotCounters();
        sender.sendMessage("[BuildStatus] counters success=" + counters.successful()
                + " failed=" + counters.failed() + " retries=" + counters.retries());

        if (village == null) {
            sender.sendMessage("[BuildStatus] No hay aldea activa.");
        } else {
            List<String> queuePreview = village.pendingBlueprintsSnapshot(5);
            sender.sendMessage("[BuildStatus] villageId=" + village.id() + " queueSize=" + village.pendingBlueprintCount()
                    + " queuePreview=" + queuePreview);

            List<Agent> builders = agentManager.all().stream()
                    .filter(agent -> agent.role() == AgentRole.BUILDER || agent.role() == AgentRole.LEADER)
                    .toList();
            if (builders.isEmpty()) {
                sender.sendMessage("[BuildStatus] No hay builders/leader asignados.");
            } else {
                String progress = builders.stream()
                        .map(agent -> "agentUuid=" + agent.villagerUuid() + ":task="
                                + (agent.activeTask() != null ? agent.activeTask().id() : "idle"))
                        .reduce((left, right) -> left + " | " + right)
                        .orElse("-");
                sender.sendMessage("[BuildStatus] progress " + progress);
            }
        }

        List<io.github.enpici.villager.life.blueprint.BuildTelemetry.BuildFailureRecord> failures =
                blueprintService.telemetry().recentFailuresSnapshot(5);
        if (failures.isEmpty()) {
            sender.sendMessage("[BuildStatus] recentErrors=none");
            return;
        }

        sender.sendMessage("[BuildStatus] recentErrors=" + failures.size());
        for (var failure : failures) {
            sender.sendMessage("- ts=" + TS_FORMAT.format(Instant.ofEpochMilli(failure.timestampMs()))
                    + " villageId=" + failure.villageId()
                    + " agentUuid=" + failure.agentUuid()
                    + " blueprintId=" + failure.blueprintId()
                    + " stepIndex=" + failure.stepIndex()
                    + " elapsedMs=" + failure.elapsedMs()
                    + " reason=" + failure.reason());
        }
    }

    private Agent resolveAgent(CommandSender sender, String identifier) {
        if (identifier != null) {
            try {
                return agentManager.find(UUID.fromString(identifier)).orElse(null);
            } catch (IllegalArgumentException ignored) {
            }
        }
        Villager villager = nearestVillager(sender);
        return villager != null ? agentManager.find(villager).orElse(null) : null;
    }

    private Villager nearestVillager(CommandSender sender) {
        if (!(sender instanceof Player player)) return null;
        Entity nearest = player.getNearbyEntities(8, 4, 8).stream()
                .filter(e -> e instanceof Villager)
                .findFirst()
                .orElse(null);
        return nearest instanceof Villager villager ? villager : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("createvillage", "register", "debug", "setrole", "build", "status", "stock", "roles", "goals", "knowledge", "family", "buildstatus", "journal", "beds", "prune");
        }
        if (args.length == 2 && "knowledge".equalsIgnoreCase(args[0])) {
            return List.of("stone", "cobblestone", "wooden_pickaxe", "stone_pickaxe", "iron_ingot", "furnace", "torch");
        }
        if (args.length == 2 && "buildstatus".equalsIgnoreCase(args[0])) {
            return List.of("reset");
        }
        if (args.length == 2 && "journal".equalsIgnoreCase(args[0])) {
            return List.of("10", "20", "40");
        }
        if (args.length == 2 && "beds".equalsIgnoreCase(args[0])) {
            return List.of("16", "32", "64");
        }
        if (args.length == 2 && "stock".equalsIgnoreCase(args[0])) {
            return List.of("16", "24", "32", "64");
        }
        if (args.length == 3 && "setrole".equalsIgnoreCase(args[0])) {
            return Arrays.stream(AgentRole.values()).map(Enum::name).toList();
        }
        return List.of();
    }

    private void saveNow() {
        if (lifeRepository != null) {
            lifeRepository.save(villageManager, agentManager);
        }
    }

    private int syncLocatedBeds(VillageAI village) {
        if (village == null) {
            return 0;
        }
        int count = bedLocator.findBedHeads(village.center(), bedScanRadius()).size();
        village.setBedCount(count);
        return count;
    }

    private int bedScanRadius() {
        VillagerLifePlugin plugin = VillagerLifePlugin.instance();
        return plugin != null ? Math.max(1, plugin.getConfig().getInt("village.bed-scan-radius", 48)) : 48;
    }

    private int resourceScanRadius() {
        VillagerLifePlugin plugin = VillagerLifePlugin.instance();
        return plugin != null
                ? Math.max(1, plugin.getConfig().getInt("village.resource-scan-radius",
                plugin.getConfig().getInt("build.nearby-container-radius", 24)))
                : 24;
    }

    private String formatLocations(List<Location> locations) {
        if (locations == null || locations.isEmpty()) {
            return "[]";
        }
        return locations.stream()
                .map(bedLocator::format)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String formatLocation(Location location) {
        return location != null ? bedLocator.format(location) : "missing";
    }
}
