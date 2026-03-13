package io.github.enpici.villager.life.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.scheduler.SimulationScheduler;
import io.github.enpici.villager.life.village.VillageAI;
import io.github.enpici.villager.life.village.VillageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class SimulationStatePersistence implements PersistenceListener {

    private final JavaPlugin plugin;
    private final AgentManager agentManager;
    private final VillageManager villageManager;
    private final BlueprintService blueprintService;
    private final SimulationScheduler simulationScheduler;
    private final VillageStateRepository villageRepository;
    private final AgentStateRepository agentRepository;
    private final BuildQueueRepository buildQueueRepository;

    private final AtomicBoolean dirtyVillage = new AtomicBoolean(false);
    private final AtomicBoolean dirtyAgents = new AtomicBoolean(false);
    private final AtomicBoolean dirtyBuildQueue = new AtomicBoolean(false);
    private BukkitTask flushTask;

    public SimulationStatePersistence(JavaPlugin plugin,
                                      AgentManager agentManager,
                                      VillageManager villageManager,
                                      BlueprintService blueprintService,
                                      SimulationScheduler simulationScheduler) {
        this.plugin = plugin;
        this.agentManager = agentManager;
        this.villageManager = villageManager;
        this.blueprintService = blueprintService;
        this.simulationScheduler = simulationScheduler;

        Path baseDir = plugin.getDataFolder().toPath().resolve("persistence");
        try {
            Files.createDirectories(baseDir);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo crear directorio de persistencia", exception);
        }

        new PersistenceMigrations(baseDir).ensureSchema();

        ObjectMapper mapper = new ObjectMapper();
        this.villageRepository = new VillageStateRepository(baseDir, mapper);
        this.agentRepository = new AgentStateRepository(baseDir, mapper);
        this.buildQueueRepository = new BuildQueueRepository(baseDir, mapper);
    }

    public void load() {
        agentManager.clearAllForRestore();
        villageManager.clearVillage();

        VillageStateRecord villageRecord = villageRepository.load().orElse(null);
        if (villageRecord != null) {
            World world = Bukkit.getWorld(villageRecord.world());
            if (world != null) {
                Location center = new Location(world, villageRecord.x(), villageRecord.y(), villageRecord.z(), villageRecord.yaw(), villageRecord.pitch());
                VillageAI village = villageManager.createVillage(villageRecord.id(), villageRecord.name(), center, agentManager, blueprintService);
                village.setPersistenceListener(this);
                village.setStateForRestore(
                        villageRecord.foodStock(),
                        villageRecord.bedCount(),
                        villageRecord.populationTarget(),
                        villageRecord.maxPopulation(),
                        villageRecord.lastThreatTick(),
                        villageRecord.lastReproductionTick(),
                        toMaterialMap(villageRecord.materialStock()),
                        toMaterialMap(villageRecord.reservedMaterials()),
                        toMaterialMap(villageRecord.pendingMaterials()),
                        villageRecord.pendingQuickBlueprints(),
                        villageRecord.pendingLongBlueprints(),
                        villageRecord.pendingMaterialRequests().stream()
                                .map(entry -> new VillageAI.MaterialRequest(Material.matchMaterial(entry.material()), entry.amount()))
                                .filter(entry -> entry.material() != null)
                                .toList());
            } else {
                plugin.getLogger().warning("No se pudo restaurar VillageAI: world no cargado '" + villageRecord.world() + "'.");
            }
        }

        agentManager.setPersistenceListener(this);
        for (AgentStateRecord record : agentRepository.load()) {
            AgentRole role = parseRole(record.role());
            Agent agent = agentManager.restore(record.villagerUuid(), role, record.npcId());
            for (NeedType needType : NeedType.values()) {
                Double value = record.needs().get(needType.name());
                if (value != null) {
                    agent.setNeedLevel(needType, value);
                }
            }
            agent.setLastEvent(record.lastEvent() == null ? "restored" : record.lastEvent());
            if (record.activeTask() != null && !record.activeTask().isBlank()) {
                agent.setLastEvent("restored-task:" + record.activeTask());
            }
        }

        BuildQueueStateRecord queueRecord = buildQueueRepository.load().orElse(null);
        if (queueRecord != null) {
            List<VillageAI.MaterialRequest> materialQueue = queueRecord.pendingMaterials().stream()
                    .map(entry -> new VillageAI.MaterialRequest(Material.matchMaterial(entry.material()), entry.amount()))
                    .filter(entry -> entry.material() != null)
                    .toList();
            simulationScheduler.restoreQueues(queueRecord.quickBlueprints(), queueRecord.longBlueprints(), materialQueue);
        }

        clearDirty();
    }

    public void startBatching() {
        long intervalTicks = Math.max(20L, plugin.getConfig().getLong("persistence.batch-interval-ticks", 100L));
        flushTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flushIfDirty, intervalTicks, intervalTicks);
    }

    public void stopBatching() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
    }

    public synchronized void flushNow() {
        VillageAI village = villageManager.currentVillage().orElse(null);
        if (village != null) {
            villageRepository.save(toVillageRecord(village));
        }
        agentRepository.save(agentManager.all().stream().map(this::toAgentRecord).toList());
        buildQueueRepository.save(toBuildQueueRecord(simulationScheduler.snapshotQueues()));
        clearDirty();
    }

    private void flushIfDirty() {
        if (!dirtyVillage.get() && !dirtyAgents.get() && !dirtyBuildQueue.get()) {
            return;
        }
        flushNow();
    }

    @Override
    public void onVillageChanged(VillageAI village) {
        dirtyVillage.set(true);
    }

    @Override
    public void onAgentChanged(Agent agent) {
        dirtyAgents.set(true);
    }

    @Override
    public void onAgentRemoved(UUID agentId) {
        dirtyAgents.set(true);
    }

    public void onBuildQueueChanged() {
        dirtyBuildQueue.set(true);
    }

    private void clearDirty() {
        dirtyVillage.set(false);
        dirtyAgents.set(false);
        dirtyBuildQueue.set(false);
    }

    private VillageStateRecord toVillageRecord(VillageAI village) {
        Location center = village.center();
        String world = center.getWorld() == null ? "" : center.getWorld().getName();
        return new VillageStateRecord(
                village.id(),
                village.name(),
                world,
                center.getX(),
                center.getY(),
                center.getZ(),
                center.getYaw(),
                center.getPitch(),
                village.foodStock(),
                village.bedCount(),
                village.populationTarget(),
                village.maxPopulation(),
                village.lastThreatTick(),
                village.lastReproductionTick(),
                toStringMap(village.materialStockSnapshot()),
                toStringMap(village.reservedMaterialsSnapshot()),
                toStringMap(village.pendingMaterials()),
                village.pendingQuickBlueprintsSnapshot(),
                village.pendingLongBlueprintsSnapshot(),
                village.pendingMaterialRequestsSnapshot().stream()
                        .map(request -> new VillageStateRecord.MaterialRequestRecord(request.material().name(), request.amount()))
                        .toList()
        );
    }

    private AgentStateRecord toAgentRecord(Agent agent) {
        String taskId = agent.activeTask() == null ? null : agent.activeTask().id();
        return new AgentStateRecord(
                agent.villagerUuid(),
                agent.role().name(),
                agent.needsSnapshot().entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue)),
                agent.npcId(),
                agent.lastEvent(),
                taskId
        );
    }

    private BuildQueueStateRecord toBuildQueueRecord(SimulationScheduler.BuildQueueSnapshot snapshot) {
        return new BuildQueueStateRecord(
                snapshot.quickBlueprints(),
                snapshot.longBlueprints(),
                snapshot.pendingMaterials().stream()
                        .map(request -> new BuildQueueStateRecord.MaterialQueueRecord(request.material().name(), request.amount()))
                        .toList()
        );
    }

    private Map<String, Integer> toStringMap(Map<Material, Integer> input) {
        return input.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));
    }

    private Map<Material, Integer> toMaterialMap(Map<String, Integer> input) {
        if (input == null) {
            return Map.of();
        }
        Map<Material, Integer> output = new ConcurrentHashMap<>();
        input.forEach((materialName, amount) -> {
            Material material = Material.matchMaterial(materialName);
            if (material != null && amount != null && amount > 0) {
                output.put(material, amount);
            }
        });
        return output;
    }

    private AgentRole parseRole(String role) {
        if (role == null) {
            return AgentRole.FARMER;
        }
        try {
            return AgentRole.valueOf(role);
        } catch (IllegalArgumentException ignored) {
            return AgentRole.FARMER;
        }
    }
}
