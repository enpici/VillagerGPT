package io.github.enpici.villager.life.scheduler;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.ai.DecisionEngine;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.event.VillageStructureBuiltEvent;
import io.github.enpici.villager.life.event.VillageStructureProgressEvent;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import io.github.enpici.villager.life.village.VillageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class SimulationScheduler {

    private final JavaPlugin plugin;
    private final AgentManager agentManager;
    private final VillageManager villageManager;
    private final DecisionEngine decisionEngine;

    private final Deque<BuildQueueItem> quickBuildQueue = new ArrayDeque<>();
    private final Deque<BuildQueueItem> longBuildQueue = new ArrayDeque<>();

    private BukkitTask taskTick;
    private BukkitTask decisionTick;
    private BukkitTask villageTick;

    public SimulationScheduler(JavaPlugin plugin, AgentManager agentManager, VillageManager villageManager, DecisionEngine decisionEngine) {
        this.plugin = plugin;
        this.agentManager = agentManager;
        this.villageManager = villageManager;
        this.decisionEngine = decisionEngine;
    }

    public void start() {
        taskTick = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickActiveTasks, 1L, 1L);
        decisionTick = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickDecisions, 20L, 20L);
        villageTick = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickVillagePlanning, 200L, 200L);
    }

    public void stop() {
        if (taskTick != null) taskTick.cancel();
        if (decisionTick != null) decisionTick.cancel();
        if (villageTick != null) villageTick.cancel();
    }

    private void tickActiveTasks() {
        VillageAI village = villageManager.currentVillage().orElse(null);
        if (village == null) return;

        for (Agent agent : agentManager.all()) {
            if (agent.activeTask() == null) continue;
            var status = agent.activeTask().tick(agent, village);
            if (status != TaskStatus.RUNNING && status != TaskStatus.PENDING) {
                agent.activeTask().stop(agent, village, status);
                agent.clearTask(status);
            }
        }

        tickBuildQueue(village);
    }

    private void tickDecisions() {
        VillageAI village = villageManager.currentVillage().orElse(null);
        if (village == null) return;

        for (Agent agent : agentManager.all()) {
            agent.decayNeeds();
            if (agent.activeTask() != null) continue;
            var newTask = decisionEngine.decide(agent, village);
            if (newTask.canStart(agent, village)) {
                newTask.start(agent, village);
                agent.assignTask(newTask);
                agent.setLastEvent("task:" + newTask.id());
            }
        }
    }

    private void tickVillagePlanning() {
        VillageAI village = villageManager.currentVillage().orElse(null);
        if (village == null) return;
        village.planVillage();
        village.ensureBasicNeedsForGrowth();
        village.tryReproduce();

        drainBuildQueues(village);
    }

    private void drainBuildQueues(VillageAI village) {
        String blueprintId;
        while ((blueprintId = village.pollPendingQuickBlueprint()) != null) {
            quickBuildQueue.offer(new BuildQueueItem(blueprintId, village.blueprintService().isCriticalBuild(blueprintId)));
        }
        while ((blueprintId = village.pollPendingLongBlueprint()) != null) {
            longBuildQueue.offer(new BuildQueueItem(blueprintId, village.blueprintService().isCriticalBuild(blueprintId)));
        }
    }

    private void tickBuildQueue(VillageAI village) {
        int maxStepsPerTick = Math.max(1, plugin.getConfig().getInt("build.max-build-steps-per-tick", 32));
        int structureTimeoutTicks = Math.max(20, plugin.getConfig().getInt("build.structure-timeout-ticks", 1_200));
        int stepTimeoutTicks = Math.max(5, plugin.getConfig().getInt("build.step-timeout-ticks", 60));
        int progressIntervalTicks = Math.max(1, plugin.getConfig().getInt("build.progress-event-interval-ticks", 20));
        int maxRetries = Math.max(0, plugin.getConfig().getInt("build.max-retries", 2));
        int stepsBudget = maxStepsPerTick;
        while (stepsBudget > 0) {
            BuildQueueItem next = quickBuildQueue.peekFirst();
            if (next == null) {
                next = longBuildQueue.peekFirst();
            }
            if (next == null) {
                return;
            }

            int processed = processBuildItem(village, next, stepsBudget, structureTimeoutTicks, stepTimeoutTicks, progressIntervalTicks, maxRetries);
            if (processed <= 0) {
                processed = 1;
            }
            stepsBudget -= processed;

            if (next.finished) {
                if (!quickBuildQueue.isEmpty() && quickBuildQueue.peekFirst() == next) {
                    quickBuildQueue.pollFirst();
                } else if (!longBuildQueue.isEmpty() && longBuildQueue.peekFirst() == next) {
                    longBuildQueue.pollFirst();
                }
            }
        }
    }

    private int processBuildItem(VillageAI village,
                                 BuildQueueItem item,
                                 int stepsBudget,
                                 int structureTimeoutTicks,
                                 int stepTimeoutTicks,
                                 int progressIntervalTicks,
                                 int maxRetries) {
        long currentTick = Bukkit.getCurrentTick();
        BlueprintService service = village.blueprintService();

        if (item.plan == null) {
            item.startedAtTick = currentTick;
            item.lastStepTick = currentTick;
            item.lastProgressTick = currentTick;
            item.status = TaskStatus.RUNNING;
            var plan = service.prepareBuildPlan(item.blueprintId, village, null, village.center());
            if (plan.isEmpty()) {
                return handleBuildFailure(item, TaskStatus.RETRYABLE_FAILED, maxRetries, service);
            }
            item.plan = plan.get();
            item.totalSteps = item.plan.pendingSteps().size();
            if (item.totalSteps == 0) {
                item.status = TaskStatus.SUCCESS;
                item.finished = true;
                Bukkit.getPluginManager().callEvent(new VillageStructureBuiltEvent(village, item.blueprintId));
                return 1;
            }
        }

        if (currentTick - item.startedAtTick > structureTimeoutTicks) {
            return handleBuildFailure(item, TaskStatus.FATAL_FAILED, maxRetries, service);
        }

        if (currentTick - item.lastStepTick > stepTimeoutTicks) {
            return handleBuildFailure(item, TaskStatus.RETRYABLE_FAILED, maxRetries, service);
        }

        int processed = 0;
        while (processed < stepsBudget && !item.plan.pendingSteps().isEmpty()) {
            BlueprintService.BuildStep step = item.plan.pendingSteps().pollFirst();
            if (step == null) {
                break;
            }

            try {
                Location location = step.location();
                if (location.getWorld() == null) {
                    return handleBuildFailure(item, TaskStatus.RETRYABLE_FAILED, maxRetries, service);
                }
                location.getBlock().setType(step.material(), false);
                if (step.blockData() != null) {
                    location.getBlock().setBlockData(step.blockData(), false);
                }
                item.placedBlocks.push(location.clone());
                item.placedSteps++;
                item.lastStepTick = currentTick;
                processed++;
            } catch (Exception exception) {
                plugin.getLogger().warning("Falló step build '" + item.blueprintId + "': " + exception.getMessage());
                return handleBuildFailure(item, TaskStatus.RETRYABLE_FAILED, maxRetries, service);
            }
        }

        if (currentTick - item.lastProgressTick >= progressIntervalTicks) {
            item.lastProgressTick = currentTick;
            Bukkit.getPluginManager().callEvent(new VillageStructureProgressEvent(village, item.blueprintId, item.placedSteps, item.totalSteps));
        }

        if (item.plan.pendingSteps().isEmpty()) {
            item.finished = true;
            item.status = TaskStatus.SUCCESS;
            Bukkit.getPluginManager().callEvent(new VillageStructureBuiltEvent(village, item.blueprintId));
        }

        return Math.max(1, processed);
    }

    private int handleBuildFailure(BuildQueueItem item, TaskStatus failureStatus, int maxRetries, BlueprintService service) {
        item.status = failureStatus;
        item.retries++;

        if (item.retries <= maxRetries && failureStatus == TaskStatus.RETRYABLE_FAILED) {
            service.rollbackMaterials(item.plan != null ? item.plan.consumedItems() : List.of());
            item.plan = null;
            item.placedBlocks.clear();
            item.placedSteps = 0;
            return 1;
        }

        if (item.critical) {
            int rollbackBlocks = Math.max(0, plugin.getConfig().getInt("build.rollback-critical-blocks", 16));
            for (int i = 0; i < rollbackBlocks && !item.placedBlocks.isEmpty(); i++) {
                Location location = item.placedBlocks.pop();
                if (location.getWorld() != null) {
                    location.getBlock().setType(Material.AIR, false);
                }
            }
        }

        service.rollbackMaterials(item.plan != null ? item.plan.consumedItems() : List.of());
        item.finished = true;
        return 1;
    }

    private static final class BuildQueueItem {
        private final String blueprintId;
        private final boolean critical;

        private BlueprintService.BuildPlan plan;
        private int retries;
        private int placedSteps;
        private int totalSteps;
        private long startedAtTick;
        private long lastStepTick;
        private long lastProgressTick;
        private boolean finished;
        private TaskStatus status = TaskStatus.PENDING;
        private final Deque<Location> placedBlocks = new ArrayDeque<>();

        private BuildQueueItem(String blueprintId, boolean critical) {
            this.blueprintId = blueprintId;
            this.critical = critical;
        }
    }
}
