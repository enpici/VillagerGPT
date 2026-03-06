package io.github.enpici.villager.life.command;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.event.AgentRoleChangedEvent;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.village.VillageAI;
import io.github.enpici.villager.life.village.VillageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class VillagerLifeCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AgentManager agentManager;
    private final VillageManager villageManager;
    private final BlueprintService blueprintService;

    public VillagerLifeCommand(AgentManager agentManager, VillageManager villageManager, BlueprintService blueprintService) {
        this.agentManager = agentManager;
        this.villageManager = villageManager;
        this.blueprintService = blueprintService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("/villagerlife <createvillage|register|debug|setrole|build|status|buildstatus>");
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
            case "buildstatus" -> handleBuildStatus(sender, args);
            default -> sender.sendMessage("Subcomando desconocido: " + sub);
        }
        return true;
    }

    private void handleCreateVillage(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return;
        }
        villageManager.createVillage("aldea_principal", player.getLocation(), agentManager, blueprintService);
        Villager first = player.getWorld().spawn(player.getLocation().add(1, 0, 0), Villager.class);
        Villager second = player.getWorld().spawn(player.getLocation().add(-1, 0, 0), Villager.class);
        agentManager.register(first, AgentRole.FARMER);
        agentManager.register(second, AgentRole.BUILDER);
        sender.sendMessage("VillageAI creada en tu posición con 2 agentes iniciales.");
    }

    private void handleRegister(CommandSender sender) {
        Villager villager = nearestVillager(sender);
        if (villager == null) {
            sender.sendMessage("No hay aldeano cerca.");
            return;
        }
        Agent agent = agentManager.register(villager, AgentRole.FARMER);
        sender.sendMessage("Aldeano registrado: " + agent.villagerUuid());
    }

    private void handleDebug(CommandSender sender, String[] args) {
        Agent agent = resolveAgent(sender, args.length > 1 ? args[1] : null);
        if (agent == null) {
            sender.sendMessage("Agente no encontrado.");
            return;
        }
        sender.sendMessage("Agent " + agent.villagerUuid() + " role=" + agent.role() + " task=" +
                (agent.activeTask() != null ? agent.activeTask().id() : "none") + " needs=" + agent.needsSnapshot());
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
        sender.sendMessage("Blueprint encolado: " + args[1]);
    }

    private void handleStatus(CommandSender sender) {
        var village = villageManager.currentVillage().orElse(null);
        if (village == null) {
            sender.sendMessage("No hay aldea activa.");
            return;
        }
        sender.sendMessage("Village " + village.name() + " pop=" + village.population() + " food=" + village.foodStock() +
                " beds=" + village.bedCount() + " agents=" + agentManager.size());
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
            return List.of("createvillage", "register", "debug", "setrole", "build", "status", "buildstatus");
        }
        if (args.length == 2 && "buildstatus".equalsIgnoreCase(args[0])) {
            return List.of("reset");
        }
        if (args.length == 3 && "setrole".equalsIgnoreCase(args[0])) {
            return Arrays.stream(AgentRole.values()).map(Enum::name).toList();
        }
        return List.of();
    }
}
