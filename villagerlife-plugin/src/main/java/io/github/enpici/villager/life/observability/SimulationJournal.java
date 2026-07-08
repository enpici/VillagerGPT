package io.github.enpici.villager.life.observability;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public class SimulationJournal {

    private final JavaPlugin plugin;
    private final boolean enabled;
    private final boolean consoleEnabled;
    private final boolean fileEnabled;
    private final int recentLimit;
    private final File logFile;
    private final Deque<String> recent = new ArrayDeque<>();

    public SimulationJournal(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("simulation-log.enabled", true);
        this.consoleEnabled = plugin.getConfig().getBoolean("simulation-log.console", true);
        this.fileEnabled = plugin.getConfig().getBoolean("simulation-log.file-enabled", true);
        this.recentLimit = Math.max(10, plugin.getConfig().getInt("simulation-log.recent-limit", 200));
        this.logFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("simulation-log.file", "simulation-story.log"));
    }

    public void record(String event, VillageAI village, Agent agent, String detail) {
        if (!enabled) {
            return;
        }

        String line = format(event, village, agent, detail);
        synchronized (recent) {
            recent.addLast(line);
            while (recent.size() > recentLimit) {
                recent.removeFirst();
            }
        }

        if (consoleEnabled) {
            plugin.getLogger().info("[Story] " + line);
        }
        if (fileEnabled) {
            append(line);
        }
    }

    public List<String> recent(int limit) {
        int effectiveLimit = Math.max(1, limit);
        synchronized (recent) {
            List<String> snapshot = new ArrayList<>(recent);
            int from = Math.max(0, snapshot.size() - effectiveLimit);
            return snapshot.subList(from, snapshot.size());
        }
    }

    public File logFile() {
        return logFile;
    }

    private String format(String event, VillageAI village, Agent agent, String detail) {
        StringBuilder builder = new StringBuilder();
        builder.append("ts=").append(Instant.now());
        builder.append(" tick=").append(currentTick());
        builder.append(" event=").append(clean(event));
        if (village != null) {
            builder.append(" village=").append(clean(village.name()));
            builder.append(" villageId=").append(shortUuid(village.id()));
            builder.append(" pop=").append(village.population());
            builder.append(" food=").append(village.foodStock());
            builder.append(" beds=").append(village.bedCount());
            builder.append(" queue=").append(village.pendingBlueprintCount());
            builder.append(" threat=").append(village.threatDetected());
        }
        if (agent != null) {
            builder.append(" agent=").append(shortUuid(agent.villagerUuid()));
            builder.append(" role=").append(agent.role());
            builder.append(" stage=").append(agent.lifeStage());
            builder.append(" goal=").append(agent.currentGoal());
            builder.append(" priority=").append(agent.currentGoalPriority());
            builder.append(" task=").append(agent.activeTask() != null ? clean(agent.activeTask().id()) : "none");
            builder.append(" needs=").append(formatNeeds(agent));
            builder.append(" loc=").append(formatLocation(agent));
        }
        if (detail != null && !detail.isBlank()) {
            builder.append(" detail=\"").append(clean(detail)).append("\"");
        }
        return builder.toString();
    }

    private String formatNeeds(Agent agent) {
        return agent.needsSnapshot().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().name().toLowerCase(Locale.ROOT) + ":" + Math.round(entry.getValue()))
                .reduce((left, right) -> left + "," + right)
                .orElse("-");
    }

    private String formatLocation(Agent agent) {
        Villager villager;
        try {
            villager = agent.resolveVillager();
        } catch (IllegalStateException | NullPointerException exception) {
            return "missing";
        }
        if (villager == null || villager.getWorld() == null) {
            return "missing";
        }
        Location location = villager.getLocation();
        return villager.getWorld().getName() + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private void append(String line) {
        try {
            File parent = logFile.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Files.writeString(
                    logFile.toPath(),
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "No se pudo escribir simulation journal: " + exception.getMessage(), exception);
        }
    }

    private long currentTick() {
        try {
            return Bukkit.getCurrentTick();
        } catch (IllegalStateException | NullPointerException exception) {
            return 0L;
        }
    }

    private String shortUuid(UUID uuid) {
        if (uuid == null) {
            return "-";
        }
        String value = uuid.toString();
        return value.substring(0, Math.min(8, value.length()));
    }

    private String clean(String value) {
        if (value == null) {
            return "-";
        }
        return value.replace('\n', ' ').replace('\r', ' ').replace('"', '\'').trim();
    }
}
