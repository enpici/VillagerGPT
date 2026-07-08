package io.github.enpici.villager.life.persistence;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentGoal;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.agent.LifeStage;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.village.VillageAI;
import io.github.enpici.villager.life.village.VillageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class LifeRepository implements AutoCloseable {

    private final JavaPlugin plugin;
    private final File dbFile;
    private Connection connection;

    public LifeRepository(JavaPlugin plugin, File dbFile) {
        this.plugin = plugin;
        this.dbFile = dbFile;
    }

    public void open() {
        try {
            dbFile.getParentFile().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS village (
                            id TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            world TEXT NOT NULL,
                            x REAL NOT NULL,
                            y REAL NOT NULL,
                            z REAL NOT NULL,
                            food_stock INTEGER NOT NULL,
                            bed_count INTEGER NOT NULL,
                            population_target INTEGER NOT NULL,
                            max_population INTEGER NOT NULL
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS agents (
                            uuid TEXT PRIMARY KEY,
                            role TEXT NOT NULL,
                            life_stage TEXT NOT NULL,
                            age_ticks INTEGER NOT NULL,
                            generation INTEGER NOT NULL,
                            parent_a TEXT,
                            parent_b TEXT,
                            partner TEXT,
                            created_at_ms INTEGER NOT NULL,
                            last_reproduction_tick INTEGER NOT NULL,
                            last_event TEXT NOT NULL,
                            last_decision_reason TEXT NOT NULL,
                            current_goal TEXT NOT NULL DEFAULT 'IDLE',
                            current_goal_reason TEXT NOT NULL DEFAULT 'loaded',
                            current_goal_priority INTEGER NOT NULL DEFAULT 0,
                            current_goal_started_tick INTEGER NOT NULL DEFAULT 0,
                            hunger REAL NOT NULL,
                            energy REAL NOT NULL,
                            safety REAL NOT NULL,
                            social REAL NOT NULL,
                            relationships TEXT NOT NULL,
                            skills TEXT NOT NULL
                        )
                        """);
                ensureColumn(statement, "agents", "current_goal", "TEXT NOT NULL DEFAULT 'IDLE'");
                ensureColumn(statement, "agents", "current_goal_reason", "TEXT NOT NULL DEFAULT 'loaded'");
                ensureColumn(statement, "agents", "current_goal_priority", "INTEGER NOT NULL DEFAULT 0");
                ensureColumn(statement, "agents", "current_goal_started_tick", "INTEGER NOT NULL DEFAULT 0");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot open VillagerLife database", exception);
        }
    }

    public void load(VillageManager villageManager, AgentManager agentManager, BlueprintService blueprintService) {
        ensureOpen();
        loadVillage(villageManager, agentManager, blueprintService);
        loadAgents(agentManager);
    }

    public void save(VillageManager villageManager, AgentManager agentManager) {
        ensureOpen();
        try {
            connection.setAutoCommit(false);
            saveVillage(villageManager);
            saveAgents(agentManager);
            connection.commit();
        } catch (SQLException exception) {
            rollbackQuietly();
            plugin.getLogger().warning("Failed to save VillagerLife state: " + exception.getMessage());
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    private void loadVillage(VillageManager villageManager, AgentManager agentManager, BlueprintService blueprintService) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM village LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return;
            }
            World world = Bukkit.getWorld(rs.getString("world"));
            if (world == null) {
                plugin.getLogger().warning("Saved village world is not loaded: " + rs.getString("world"));
                return;
            }
            VillageAI village = villageManager.restoreVillage(
                    UUID.fromString(rs.getString("id")),
                    rs.getString("name"),
                    new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z")),
                    agentManager,
                    blueprintService
            );
            village.setFoodStock(rs.getInt("food_stock"));
            village.setBedCount(rs.getInt("bed_count"));
            village.setPopulationTarget(rs.getInt("population_target"));
            village.setMaxPopulation(rs.getInt("max_population"));
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to load village state: " + exception.getMessage());
        }
    }

    private void loadAgents(AgentManager agentManager) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM agents");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Agent agent = new Agent(UUID.fromString(rs.getString("uuid")), AgentRole.valueOf(rs.getString("role")));
                agent.setLifeStage(LifeStage.valueOf(rs.getString("life_stage")));
                agent.setAgeTicks(rs.getLong("age_ticks"));
                agent.setGeneration(rs.getInt("generation"));
                agent.setParents(parseUuid(rs.getString("parent_a")), parseUuid(rs.getString("parent_b")));
                agent.setPartner(parseUuid(rs.getString("partner")));
                agent.setCreatedAtMs(rs.getLong("created_at_ms"));
                agent.setLastReproductionTick(rs.getLong("last_reproduction_tick"));
                agent.setLastEvent(rs.getString("last_event"));
                agent.setLastDecisionReason(rs.getString("last_decision_reason"));
                agent.assignGoal(
                        parseGoal(rs.getString("current_goal")),
                        rs.getInt("current_goal_priority"),
                        rs.getString("current_goal_reason"),
                        rs.getLong("current_goal_started_tick")
                );
                agent.setLastEvent(rs.getString("last_event"));
                agent.setNeed(NeedType.HUNGER, rs.getDouble("hunger"));
                agent.setNeed(NeedType.ENERGY, rs.getDouble("energy"));
                agent.setNeed(NeedType.SAFETY, rs.getDouble("safety"));
                agent.setNeed(NeedType.SOCIAL, rs.getDouble("social"));
                parseRelationships(rs.getString("relationships"), agent);
                parseSkills(rs.getString("skills"), agent);
                agentManager.restore(agent);
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to load agent state: " + exception.getMessage());
        }
    }

    private void saveVillage(VillageManager villageManager) throws SQLException {
        VillageAI village = villageManager.currentVillage().orElse(null);
        if (village == null || village.center().getWorld() == null) {
            return;
        }
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM village")) {
            delete.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO village (id, name, world, x, y, z, food_stock, bed_count, population_target, max_population)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            Location center = village.center();
            ps.setString(1, village.id().toString());
            ps.setString(2, village.name());
            ps.setString(3, center.getWorld().getName());
            ps.setDouble(4, center.getX());
            ps.setDouble(5, center.getY());
            ps.setDouble(6, center.getZ());
            ps.setInt(7, village.foodStock());
            ps.setInt(8, village.bedCount());
            ps.setInt(9, village.populationTarget());
            ps.setInt(10, village.maxPopulation());
            ps.executeUpdate();
        }
    }

    private void saveAgents(AgentManager agentManager) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM agents")) {
            delete.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO agents (
                    uuid, role, life_stage, age_ticks, generation, parent_a, parent_b, partner,
                    created_at_ms, last_reproduction_tick, last_event, last_decision_reason,
                    current_goal, current_goal_reason, current_goal_priority, current_goal_started_tick,
                    hunger, energy, safety, social, relationships, skills
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (Agent agent : agentManager.all()) {
                ps.setString(1, agent.villagerUuid().toString());
                ps.setString(2, agent.role().name());
                ps.setString(3, agent.lifeStage().name());
                ps.setLong(4, agent.ageTicks());
                ps.setInt(5, agent.generation());
                ps.setString(6, formatUuid(agent.parentA()));
                ps.setString(7, formatUuid(agent.parentB()));
                ps.setString(8, formatUuid(agent.partner()));
                ps.setLong(9, agent.createdAtMs());
                ps.setLong(10, agent.lastReproductionTick());
                ps.setString(11, agent.lastEvent());
                ps.setString(12, agent.lastDecisionReason());
                ps.setString(13, agent.currentGoal().name());
                ps.setString(14, agent.currentGoalReason());
                ps.setInt(15, agent.currentGoalPriority());
                ps.setLong(16, agent.currentGoalStartedTick());
                ps.setDouble(17, agent.needLevel(NeedType.HUNGER));
                ps.setDouble(18, agent.needLevel(NeedType.ENERGY));
                ps.setDouble(19, agent.needLevel(NeedType.SAFETY));
                ps.setDouble(20, agent.needLevel(NeedType.SOCIAL));
                ps.setString(21, serializeRelationships(agent));
                ps.setString(22, serializeSkills(agent));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private String serializeRelationships(Agent agent) {
        return agent.relationshipsSnapshot().entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    private String serializeSkills(Agent agent) {
        return agent.skillXpSnapshot().entrySet().stream()
                .map(entry -> entry.getKey().name() + ":" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    private void parseRelationships(String value, Agent agent) {
        if (value == null || value.isBlank()) return;
        for (String entry : value.split(",")) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2) {
                try {
                    agent.setRelationship(UUID.fromString(parts[0]), Integer.parseInt(parts[1]));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void parseSkills(String value, Agent agent) {
        if (value == null || value.isBlank()) return;
        for (String entry : value.split(",")) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2) {
                try {
                    agent.setSkillXp(AgentRole.valueOf(parts[0]), Integer.parseInt(parts[1]));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        return UUID.fromString(value);
    }

    private AgentGoal parseGoal(String value) {
        if (value == null || value.isBlank()) return AgentGoal.IDLE;
        try {
            return AgentGoal.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return AgentGoal.IDLE;
        }
    }

    private String formatUuid(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }

    private void ensureColumn(Statement statement, String table, String column, String definition) throws SQLException {
        if (columnExists(table, column)) {
            return;
        }
        statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, table, column)) {
            if (rs.next()) {
                return true;
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private void ensureOpen() {
        if (connection == null) {
            throw new IllegalStateException("LifeRepository is not open");
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
