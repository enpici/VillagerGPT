package io.github.enpici.villager.life.scheduler;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentGoal;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.ai.AgentAiPlanner;
import io.github.enpici.villager.life.ai.AgentSkillCatalog;
import io.github.enpici.villager.life.ai.AgentToolExecutor;
import io.github.enpici.villager.life.ai.AgentPlanner;
import io.github.enpici.villager.life.ai.DecisionEngine;
import io.github.enpici.villager.life.ai.ReproductionService;
import io.github.enpici.villager.life.ai.RoleAssignmentService;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.event.VillageStructureBuiltEvent;
import io.github.enpici.villager.life.event.VillageStructureProgressEvent;
import io.github.enpici.villager.life.event.AgentRoleChangedEvent;
import io.github.enpici.villager.life.observability.SimulationJournal;
import io.github.enpici.villager.life.persistence.LifeRepository;
import io.github.enpici.villager.life.planning.AiShelterPlanner;
import io.github.enpici.villager.life.planning.ShelterBlueprintFactory;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import io.github.enpici.villager.life.village.BedLocator;
import io.github.enpici.villager.life.village.PhysicalResourceScanner;
import io.github.enpici.villager.life.village.VillageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

public class SimulationScheduler {

    private final JavaPlugin plugin;
    private final AgentManager agentManager;
    private final VillageManager villageManager;
    private final AgentPlanner agentPlanner;
    private final DecisionEngine decisionEngine;
    private final ReproductionService reproductionService;
    private final RoleAssignmentService roleAssignmentService;
    private final LifeRepository lifeRepository;
    private final SimulationJournal journal;
    private final AiShelterPlanner aiShelterPlanner;
    private final AgentAiPlanner agentAiPlanner;
    private final AgentSkillCatalog agentSkillCatalog = new AgentSkillCatalog();
    private final AgentToolExecutor agentToolExecutor = new AgentToolExecutor();
    private final ShelterBlueprintFactory shelterBlueprintFactory = new ShelterBlueprintFactory();
    private final BedLocator bedLocator = new BedLocator();
    private final PhysicalResourceScanner physicalResourceScanner = new PhysicalResourceScanner();
    private final Map<UUID, TaskWatch> taskWatches = new ConcurrentHashMap<>();
    private final Map<UUID, MissingEntityWatch> missingEntityWatches = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> shelterPlanningInFlight = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> agentPlanningInFlight = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAgentAiPlanTick = new ConcurrentHashMap<>();
    private final Map<UUID, AiGoalHold> aiGoalHolds = new ConcurrentHashMap<>();

    private final Deque<BuildQueueItem> quickBuildQueue = new ArrayDeque<>();
    private final Deque<BuildQueueItem> longBuildQueue = new ArrayDeque<>();

    private BukkitTask taskTick;
    private BukkitTask decisionTick;
    private BukkitTask villageTick;
    private BukkitTask autosaveTick;

    public SimulationScheduler(JavaPlugin plugin,
                               AgentManager agentManager,
                               VillageManager villageManager,
                               AgentPlanner agentPlanner,
                               DecisionEngine decisionEngine,
                               ReproductionService reproductionService,
                               RoleAssignmentService roleAssignmentService,
                               LifeRepository lifeRepository,
                               SimulationJournal journal,
                               AiShelterPlanner aiShelterPlanner,
                               AgentAiPlanner agentAiPlanner) {
        this.plugin = plugin;
        this.agentManager = agentManager;
        this.villageManager = villageManager;
        this.agentPlanner = agentPlanner;
        this.decisionEngine = decisionEngine;
        this.reproductionService = reproductionService;
        this.roleAssignmentService = roleAssignmentService;
        this.lifeRepository = lifeRepository;
        this.journal = journal;
        this.aiShelterPlanner = aiShelterPlanner;
        this.agentAiPlanner = agentAiPlanner;
    }

    public void start() {
        long taskInterval = Math.max(1L, plugin.getConfig().getLong("simulation.task-interval-ticks", 1L));
        long decisionInterval = Math.max(1L, plugin.getConfig().getLong("simulation.decision-interval-ticks", 20L));
        long villageInterval = Math.max(1L, plugin.getConfig().getLong("simulation.village-planning-interval-ticks", 200L));
        long autosaveInterval = Math.max(20L, plugin.getConfig().getLong("persistence.autosave-interval-ticks", 1_200L));
        taskTick = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickActiveTasks, 1L, taskInterval);
        decisionTick = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickDecisions, decisionInterval, decisionInterval);
        villageTick = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickVillagePlanning, villageInterval, villageInterval);
        autosaveTick = plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveState, autosaveInterval, autosaveInterval);
    }

    public void stop() {
        if (taskTick != null) taskTick.cancel();
        if (decisionTick != null) decisionTick.cancel();
        if (villageTick != null) villageTick.cancel();
        if (autosaveTick != null) autosaveTick.cancel();
        saveState();
    }

    private void tickActiveTasks() {
        VillageAI village = villageManager.currentVillage().orElse(null);
        if (village == null) return;
        keepVillageChunksLoaded(village);

        for (Agent agent : agentManager.all()) {
            if (handleMissingEntity(agent, village, "task_tick")) {
                continue;
            }
            if (agent.activeTask() == null) continue;
            String taskId = agent.activeTask().id();
            var status = agent.activeTask().tick(agent, village);
            if (status == TaskStatus.RUNNING) {
                detectStuckAgent(agent, village, taskId);
            }
            if (status != TaskStatus.RUNNING && status != TaskStatus.PENDING) {
                String taskLastEvent = agent.lastEvent();
                agent.activeTask().stop(agent, village, status);
                agent.clearTask(status);
                TaskWatch watch = taskWatches.remove(agent.villagerUuid());
                String goalAtStart = watch != null ? watch.goalAtStart : agent.currentGoal().name();
                journal.record(
                        status == TaskStatus.SUCCESS ? "task_completed" : "task_failed",
                        village,
                        agent,
                        "task=" + taskId + " status=" + status + " goalAtStart=" + goalAtStart + " currentGoal=" + agent.currentGoal()
                                + " lastEvent=" + taskLastEvent
                );
                if (status == TaskStatus.SUCCESS && goalAtStart.equals(agent.currentGoal().name()) && agent.currentGoalPriority() > 0) {
                    journal.record("goal_progress", village, agent, "goal=" + goalAtStart + " satisfied by task=" + taskId);
                } else if (status == TaskStatus.SUCCESS && !goalAtStart.equals(agent.currentGoal().name())) {
                    journal.record("stale_task_completed", village, agent, "task=" + taskId + " goalAtStart=" + goalAtStart + " currentGoal=" + agent.currentGoal());
                }
            }
        }

        tickBuildQueue(village);
    }

    private void tickDecisions() {
        VillageAI village = villageManager.currentVillage().orElse(null);
        if (village == null) return;
        keepVillageChunksLoaded(village);
        syncPhysicalResources(village, "decision_tick");

        for (Agent agent : agentManager.all()) {
            if (handleMissingEntity(agent, village, "decision_tick")) {
                continue;
            }
            tickLifecycle(agent, village);
            agent.decayNeeds();
            var previousGoal = agent.currentGoal();
            var previousReason = agent.currentGoalReason();
            int previousPriority = agent.currentGoalPriority();
            if (!applyAiGoalHold(agent, village)) {
                agentPlanner.plan(agent, village);
            }
            if (previousGoal != agent.currentGoal()
                    || previousPriority != agent.currentGoalPriority()
                    || !previousReason.equals(agent.currentGoalReason())) {
                journal.record(
                        "goal_changed",
                        village,
                        agent,
                        "from=" + previousGoal + " to=" + agent.currentGoal()
                                + " priority=" + agent.currentGoalPriority()
                                + " reason=" + agent.currentGoalReason()
                );
            }
            requestAgentPlanIfNeeded(agent, village);
            if (agent.activeTask() != null) continue;
            var newTask = decisionEngine.decide(agent, village);
            if (newTask.canStart(agent, village)) {
                newTask.start(agent, village);
                agent.assignTask(newTask);
                agent.setLastEvent("task:" + newTask.id());
                agent.addSkillXp(agent.role(), 1);
                taskWatches.put(agent.villagerUuid(), TaskWatch.start(newTask.id(), agent.currentGoal().name(), locationKey(agent), agent.lastEvent()));
                journal.record("task_started", village, agent, "task=" + newTask.id() + " decision=" + agent.lastDecisionReason());
            } else {
                journal.record("task_blocked", village, agent, "task=" + newTask.id() + " decision=" + agent.lastDecisionReason());
            }
        }
    }

    private void requestAgentPlanIfNeeded(Agent agent, VillageAI village) {
        if (agentAiPlanner == null || !agentAiPlanner.enabled()) {
            return;
        }
        if (agent == null || village == null || agent.missingEntity()) {
            return;
        }

        long tick = currentTick();
        long lastTick = lastAgentAiPlanTick.getOrDefault(agent.villagerUuid(), -agentAiPlanner.cooldownTicks());
        if (tick - lastTick < agentAiPlanner.cooldownTicks()) {
            return;
        }
        if (agentPlanningInFlight.size() >= agentAiPlanner.maxConcurrentRequests()) {
            return;
        }
        if (agentPlanningInFlight.putIfAbsent(agent.villagerUuid(), true) != null) {
            return;
        }

        lastAgentAiPlanTick.put(agent.villagerUuid(), tick);
        AgentGoal deterministicGoal = agent.currentGoal();
        int deterministicPriority = agent.currentGoalPriority();
        journal.record(
                "ai_agent_plan_requested",
                village,
                agent,
                "deterministicGoal=" + deterministicGoal + " deterministicPriority=" + deterministicPriority
        );

        agentAiPlanner.planGoal(agent, village, deterministicGoal.name()).whenComplete((plan, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    agentPlanningInFlight.remove(agent.villagerUuid());
                    Agent liveAgent = agentManager.find(agent.villagerUuid()).orElse(null);
                    VillageAI liveVillage = villageManager.currentVillage().orElse(null);
                    if (liveAgent == null || liveVillage == null || !liveVillage.id().equals(village.id())) {
                        return;
                    }
                    if (error != null || plan == null) {
                        journal.record("ai_agent_plan_failed", liveVillage, liveAgent, "error=" + (error != null ? error.getClass().getSimpleName() : "null_plan"));
                        return;
                    }
                    AgentPlanValidation validation = validateAgentPlan(liveAgent, liveVillage, plan);
                    if (!validation.accepted()) {
                        journal.record(
                                "ai_agent_plan_rejected",
                                liveVillage,
                                liveAgent,
                                "goal=" + plan.goal() + " priority=" + plan.priority() + " reason=" + plan.reason()
                                        + " rejection=" + validation.reason()
                        );
                        return;
                    }

                    AgentGoal previous = liveAgent.currentGoal();
                    int previousPriority = liveAgent.currentGoalPriority();
                    String previousReason = liveAgent.currentGoalReason();
                    AgentGoal effectiveGoal = plan.goal();
                    AgentToolExecutor.ToolExecution toolExecution = null;
                    if (plan.tool() != null) {
                        toolExecution = agentToolExecutor.apply(liveVillage, plan.tool());
                        if (toolExecution.accepted()) {
                            if (toolExecution.impliedGoal() != null && toolExecution.impliedGoal() != AgentGoal.IDLE) {
                                effectiveGoal = toolExecution.impliedGoal();
                            }
                            journal.record(
                                    "ai_tool_accepted",
                                    liveVillage,
                                    liveAgent,
                                    "skill=" + cleanPlanPart(plan.skill())
                                            + " tool=" + plan.tool().compact()
                                            + " args=" + plan.tool().args()
                                            + " impliedGoal=" + toolExecution.impliedGoal()
                                            + " detail=" + toolExecution.detail()
                            );
                        } else {
                            journal.record(
                                    "ai_tool_rejected",
                                    liveVillage,
                                    liveAgent,
                                    "skill=" + cleanPlanPart(plan.skill())
                                            + " tool=" + plan.tool().compact()
                                            + " args=" + plan.tool().args()
                                            + " detail=" + toolExecution.detail()
                            );
                        }
                    }

                    if (shouldInterruptForAiRecovery(liveAgent, liveVillage, effectiveGoal)) {
                        String interruptedTask = liveAgent.activeTask().id();
                        liveAgent.activeTask().stop(liveAgent, liveVillage, TaskStatus.RETRYABLE_FAILED);
                        liveAgent.clearTask(TaskStatus.RETRYABLE_FAILED);
                        taskWatches.remove(liveAgent.villagerUuid());
                        journal.record(
                                "ai_task_interrupted",
                                liveVillage,
                                liveAgent,
                                "task=" + interruptedTask + " newGoal=" + effectiveGoal + " reason=no_food_recovery"
                        );
                    }
                    boolean changed = liveAgent.assignGoal(
                            effectiveGoal,
                            plan.priority(),
                            "ai: " + plan.reason() + planExecutionSuffix(plan, toolExecution),
                            currentTick()
                    );
                    aiGoalHolds.put(
                            liveAgent.villagerUuid(),
                            new AiGoalHold(effectiveGoal, plan.priority(), plan.reason(), currentTick() + aiGoalHoldTicks())
                    );
                    journal.record(
                            changed ? "ai_agent_plan_accepted" : "ai_agent_plan_confirmed",
                            liveVillage,
                            liveAgent,
                            "from=" + previous + "/" + previousPriority
                                    + " to=" + effectiveGoal + "/" + plan.priority()
                                    + " requestedGoal=" + plan.goal()
                                    + " skill=" + cleanPlanPart(plan.skill())
                                    + " tool=" + (plan.tool() != null ? plan.tool().compact() : "none")
                                    + " previousReason=" + previousReason
                                    + " reason=" + plan.reason()
                    );
                    if (changed) {
                        saveState();
                    }
                })
        );
    }

    private AgentPlanValidation validateAgentPlan(Agent agent, VillageAI village, AgentAiPlanner.AgentPlan plan) {
        if (!plan.accepted()) {
            return AgentPlanValidation.rejected(plan.rejectionReason());
        }
        if (plan.goal() == null) {
            return AgentPlanValidation.rejected("missing_goal");
        }
        if (plan.skill() != null && !plan.skill().isBlank() && agentSkillCatalog.find(plan.skill()).isEmpty()) {
            return AgentPlanValidation.rejected("invalid_skill:" + cleanPlanPart(plan.skill()));
        }
        if (plan.priority() < agentAiPlanner.minAcceptedPriority()) {
            return AgentPlanValidation.rejected("priority_too_low");
        }
        if (!agent.isAdult() && !isChildGoal(plan.goal())) {
            return AgentPlanValidation.rejected("child_cannot_use_adult_goal");
        }
        if (isCriticalPersonalGoal(agent.currentGoal(), agent.currentGoalPriority())
                && !isAllowedNoFoodRecovery(agent, village, plan.goal())
                && plan.goal() != agent.currentGoal()
                && plan.priority() <= agent.currentGoalPriority()) {
            return AgentPlanValidation.rejected("deterministic_emergency_has_priority");
        }
        if (village.threatDetected()
                && !isThreatResponseGoal(plan.goal())
                && plan.priority() <= 95) {
            return AgentPlanValidation.rejected("threat_requires_safety_response");
        }
        if (agent.needLevel(NeedType.HUNGER) >= 85
                && village.foodStock() > 0
                && plan.goal() != AgentGoal.SURVIVE_HUNGER
                && plan.priority() <= 95) {
            return AgentPlanValidation.rejected("critical_hunger_with_food_requires_eating");
        }
        return AgentPlanValidation.ok();
    }

    private String planExecutionSuffix(AgentAiPlanner.AgentPlan plan, AgentToolExecutor.ToolExecution toolExecution) {
        StringBuilder builder = new StringBuilder();
        if (plan.skill() != null && !plan.skill().isBlank()) {
            builder.append(" skill=").append(cleanPlanPart(plan.skill()));
        }
        if (plan.tool() != null) {
            builder.append(" tool=").append(plan.tool().compact());
        }
        if (toolExecution != null && toolExecution.accepted()) {
            builder.append(" executed=").append(toolExecution.detail());
        }
        return builder.toString();
    }

    private String cleanPlanPart(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.replace('\n', ' ').replace('\r', ' ').replace('"', '\'').trim();
    }

    private boolean applyAiGoalHold(Agent agent, VillageAI village) {
        AiGoalHold hold = aiGoalHolds.get(agent.villagerUuid());
        if (hold == null) {
            return false;
        }
        long tick = currentTick();
        if (tick > hold.expiresAtTick()) {
            aiGoalHolds.remove(agent.villagerUuid());
            return false;
        }
        if (mustOverrideAiHold(agent, village, hold.goal())) {
            aiGoalHolds.remove(agent.villagerUuid());
            return false;
        }
        agent.assignGoal(hold.goal(), hold.priority(), "ai: " + hold.reason(), tick);
        return true;
    }

    private boolean mustOverrideAiHold(Agent agent, VillageAI village, AgentGoal goal) {
        if (village.threatDetected() && !isThreatResponseGoal(goal)) {
            return true;
        }
        return agent.needLevel(NeedType.HUNGER) >= 85
                && village.foodStock() > 0
                && goal != AgentGoal.SURVIVE_HUNGER;
    }

    private boolean shouldInterruptForAiRecovery(Agent agent, VillageAI village, AgentGoal goal) {
        return plugin.getConfig().getBoolean("ai-planning.agent-decisions.interrupt-active-task", true)
                && agent.activeTask() != null
                && isAllowedNoFoodRecovery(agent, village, goal);
    }

    private boolean isAllowedNoFoodRecovery(Agent agent, VillageAI village, AgentGoal goal) {
        return agent.currentGoal() == AgentGoal.SURVIVE_HUNGER
                && agent.needLevel(NeedType.HUNGER) >= 85
                && village.foodStock() == 0
                && isNoFoodRecoveryGoal(goal);
    }

    private boolean isNoFoodRecoveryGoal(AgentGoal goal) {
        return goal == AgentGoal.WORK_FOOD
                || goal == AgentGoal.TRADE_GOODS
                || goal == AgentGoal.GATHER_MATERIALS
                || goal == AgentGoal.CRAFT_SUPPLIES;
    }

    private long aiGoalHoldTicks() {
        return Math.max(20L, plugin.getConfig().getLong("ai-planning.agent-decisions.hold-ticks", 200L));
    }

    private boolean isCriticalPersonalGoal(AgentGoal goal, int priority) {
        return priority >= 90 && (goal == AgentGoal.SURVIVE_HUNGER || goal == AgentGoal.SEEK_SAFETY);
    }

    private boolean isThreatResponseGoal(AgentGoal goal) {
        return goal == AgentGoal.SEEK_SAFETY
                || goal == AgentGoal.PATROL
                || goal == AgentGoal.BUILD_SHELTER;
    }

    private boolean isChildGoal(AgentGoal goal) {
        return goal == AgentGoal.GROW_UP
                || goal == AgentGoal.SOCIALIZE
                || goal == AgentGoal.REST
                || goal == AgentGoal.SURVIVE_HUNGER
                || goal == AgentGoal.SEEK_SAFETY
                || goal == AgentGoal.IDLE;
    }

    private void tickVillagePlanning() {
        VillageAI village = villageManager.currentVillage().orElse(null);
        if (village == null) return;
        keepVillageChunksLoaded(village);
        syncPhysicalBeds(village);
        syncPhysicalResources(village, "village_tick");
        village.planVillage();
        village.ensureBasicNeedsForGrowth();
        requestShelterPlanIfNeeded(village);
        var baby = reproductionService.tryReproduce(village);
        if (baby != null) {
            var child = agentManager.find(baby).orElse(null);
            journal.record("villager_born", village, child, "baby=" + baby.getUniqueId());
        }
        journal.record(
                "village_summary",
                village,
                null,
                "agents=" + agentManager.size()
                        + " missingAgents=" + agentManager.all().stream().filter(Agent::missingEntity).count()
                        + " physicalBeds=" + bedLocator.findBedHeads(village.center(), bedScanRadius()).size()
                        + " pendingMaterials=" + village.pendingMaterials()
                        + " reserved=" + village.reservedMaterialsSnapshot()
                        + " stock=" + village.materialStockSnapshot()
        );

        drainBuildQueues(village);
    }

    private void syncPhysicalBeds(VillageAI village) {
        int physicalBeds = bedLocator.findBedHeads(village.center(), bedScanRadius()).size();
        if (physicalBeds != village.bedCount()) {
            int previous = village.bedCount();
            village.setBedCount(physicalBeds);
            journal.record("beds_synced", village, null, "from=" + previous + " to=" + physicalBeds + " radius=" + bedScanRadius());
            saveState();
        }
    }

    private int bedScanRadius() {
        return Math.max(1, plugin.getConfig().getInt("village.bed-scan-radius", 48));
    }

    private void syncPhysicalResources(VillageAI village, String phase) {
        if (village.center().getWorld() == null) {
            return;
        }
        int radius = resourceScanRadius();
        var snapshot = physicalResourceScanner.scan(village, agentManager.all(), radius);
        Map<Material, Integer> previousStock = village.materialStockSnapshot();
        int previousFood = village.foodStock();
        boolean stockChanged = village.replaceMaterialStock(snapshot.materials());
        if (previousFood != snapshot.foodPoints()) {
            village.setFoodStock(snapshot.foodPoints());
        }

        if (stockChanged || previousFood != snapshot.foodPoints()) {
            journal.record(
                    "resources_synced",
                    village,
                    null,
                    "phase=" + phase
                            + " radius=" + radius
                            + " containers=" + snapshot.containerCount()
                            + " agentInventories=" + snapshot.agentInventoryCount()
                            + " food=" + previousFood + "->" + snapshot.foodPoints()
                            + " stock=" + previousStock + "->" + village.materialStockSnapshot()
            );
            saveState();
        }
    }

    private int resourceScanRadius() {
        return Math.max(1, plugin.getConfig().getInt("village.resource-scan-radius",
                plugin.getConfig().getInt("build.nearby-container-radius", 24)));
    }

    private void requestShelterPlanIfNeeded(VillageAI village) {
        if (aiShelterPlanner == null || !aiShelterPlanner.enabled()) {
            return;
        }
        if (village.center().getWorld() == null) {
            return;
        }
        if (!hasPhysicalAgents()) {
            return;
        }
        boolean triggerThreat = plugin.getConfig().getBoolean("ai-planning.shelter.trigger-on-threat", true) && village.threatDetected();
        boolean triggerNight = plugin.getConfig().getBoolean("ai-planning.shelter.trigger-at-night", true) && !village.center().getWorld().isDayTime();
        if (!triggerThreat && !triggerNight) {
            return;
        }
        if (village.pendingBlueprintsSnapshot(10).stream().anyMatch(id -> id.startsWith("ai_shelter"))) {
            return;
        }
        long tick = currentTick();
        long cooldown = Math.max(200L, plugin.getConfig().getLong("ai-planning.shelter.cooldown-ticks", 6_000L));
        if (tick - village.lastShelterPlanTick() < cooldown) {
            return;
        }
        if (shelterPlanningInFlight.putIfAbsent(village.id(), true) != null) {
            return;
        }

        String trigger = triggerThreat ? "threat" : "night";
        journal.record("ai_plan_requested", village, null, "kind=shelter trigger=" + trigger);
        aiShelterPlanner.planShelter(village, trigger).whenComplete((plan, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            shelterPlanningInFlight.remove(village.id());
            if (error != null || plan == null) {
                journal.record("ai_plan_failed", village, null, "kind=shelter error=" + (error != null ? error.getClass().getSimpleName() : "null_plan"));
                return;
            }
            String blueprintId = "ai_shelter_" + currentTick() + "_" + village.shelterCount();
            var buildPlan = shelterBlueprintFactory.create(village, plan);
            village.blueprintService().registerDynamicBuildPlan(blueprintId, buildPlan);
            village.enqueueBlueprint(blueprintId);
            village.markShelterPlanned();
            journal.record(
                    "ai_plan_accepted",
                    village,
                    null,
                    "kind=shelter id=" + blueprintId
                            + " source=" + plan.source()
                            + " size=" + plan.width() + "x" + plan.height() + "x" + plan.depth()
                            + " wall=" + plan.wallMaterial()
                            + " roof=" + plan.roofMaterial()
                            + " reason=" + plan.reason()
            );
            saveState();
        }));
    }

    private boolean hasPhysicalAgents() {
        for (Agent agent : agentManager.all()) {
            try {
                var villager = agent.resolveVillager();
                if (villager != null && villager.isValid()) {
                    return true;
                }
            } catch (IllegalStateException | NullPointerException ignored) {
                agent.setMissingEntity(true);
            }
        }
        return false;
    }

    private void tickLifecycle(Agent agent, VillageAI village) {
        long adultAgeTicks = Math.max(1L, plugin.getConfig().getLong("lifecycle.adult-age-ticks", 24_000L));
        boolean becameAdult = agent.tickAge(adultAgeTicks);
        if (!becameAdult) {
            return;
        }

        agentManager.syncVisualAge(agent);
        AgentRole previous = agent.role();
        AgentRole next = roleAssignmentService.chooseRoleForAdult(agent, village);
        agent.setRole(next);
        agent.setLastDecisionReason("became adult and received role " + next.name());
        if (previous != next) {
            Bukkit.getPluginManager().callEvent(new AgentRoleChangedEvent(agent, previous, next));
            journal.record("role_changed", village, agent, "from=" + previous + " to=" + next + " reason=became_adult");
        }
    }

    private void detectStuckAgent(Agent agent, VillageAI village, String taskId) {
        int stuckTicks = Math.max(20, plugin.getConfig().getInt("simulation-log.stuck-task-ticks", 200));
        TaskWatch watch = taskWatches.computeIfAbsent(agent.villagerUuid(), uuid -> TaskWatch.start(taskId, agent.currentGoal().name(), locationKey(agent), agent.lastEvent()));
        String currentLocation = locationKey(agent);
        String currentEvent = agent.lastEvent();
        if (!watch.taskId.equals(taskId) || !watch.locationKey.equals(currentLocation) || !watch.lastEvent.equals(currentEvent)) {
            taskWatches.put(agent.villagerUuid(), TaskWatch.start(taskId, watch.goalAtStart, currentLocation, currentEvent));
            return;
        }

        watch.sameTicks++;
        if (watch.sameTicks >= stuckTicks && watch.sameTicks % stuckTicks == 0) {
            journal.record(
                    "agent_stuck",
                    village,
                    agent,
                    "task=" + taskId + " sameTicks=" + watch.sameTicks + " location=" + currentLocation + " lastEvent=" + currentEvent
            );
        }
    }

    private boolean handleMissingEntity(Agent agent, VillageAI village, String phase) {
        if (agent == null) {
            return true;
        }

        try {
            var villager = agent.resolveVillager();
            if (villager != null && villager.isValid()) {
                keepChunkLoaded(villager.getLocation());
                MissingEntityWatch recovered = missingEntityWatches.remove(agent.villagerUuid());
                if (recovered != null) {
                    journal.record("agent_restored", village, agent, "phase=" + phase + " missingTicks=" + recovered.missingTicks(currentTick()));
                }
                return false;
            }
        } catch (IllegalStateException | NullPointerException ignored) {
            agent.setMissingEntity(true);
        }

        long tick = currentTick();
        MissingEntityWatch watch = missingEntityWatches.computeIfAbsent(agent.villagerUuid(), uuid -> new MissingEntityWatch(tick));
        int pruneAfterTicks = Math.max(0, plugin.getConfig().getInt("simulation-log.missing-agent-prune-ticks", 400));
        long missingTicks = watch.missingTicks(tick);

        if (!watch.reportedMissing) {
            watch.reportedMissing = true;
            journal.record("agent_missing", village, agent, "phase=" + phase + " pruneAfterTicks=" + pruneAfterTicks);
        }

        if (pruneAfterTicks > 0 && missingTicks >= pruneAfterTicks) {
            if (agent.activeTask() != null) {
                agent.clearTask(TaskStatus.FATAL_FAILED);
            }
            journal.record("agent_pruned", village, agent, "phase=" + phase + " missingTicks=" + missingTicks + " reason=entity_not_found");
            taskWatches.remove(agent.villagerUuid());
            missingEntityWatches.remove(agent.villagerUuid());
            agentManager.unregister(agent.villagerUuid());
            saveState();
        }
        return true;
    }

    private void keepVillageChunksLoaded(VillageAI village) {
        if (!plugin.getConfig().getBoolean("village.keep-loaded", true)) {
            return;
        }
        Location center = village.center();
        if (center.getWorld() == null) {
            return;
        }
        int radius = Math.max(0, plugin.getConfig().getInt("village.keep-loaded-radius-chunks", 2));
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
                keepChunkLoaded(center.getWorld().getChunkAt(x, z));
            }
        }
    }

    private void keepChunkLoaded(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        keepChunkLoaded(location.getChunk());
    }

    private void keepChunkLoaded(org.bukkit.Chunk chunk) {
        if (chunk == null || !plugin.getConfig().getBoolean("village.keep-loaded", true)) {
            return;
        }
        if (!chunk.isLoaded()) {
            chunk.load();
        }
        chunk.addPluginChunkTicket(plugin);
    }

    private String locationKey(Agent agent) {
        try {
            var villager = agent.resolveVillager();
            if (villager == null || villager.getWorld() == null) {
                return "missing";
            }
            var location = villager.getLocation();
            return villager.getWorld().getName() + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
        } catch (IllegalStateException | NullPointerException exception) {
            return "missing";
        }
    }

    private void saveState() {
        if (lifeRepository != null) {
            lifeRepository.save(villageManager, agentManager);
        }
    }

    private long currentTick() {
        try {
            return Bukkit.getCurrentTick();
        } catch (IllegalStateException | NullPointerException exception) {
            return 0L;
        }
    }

    private void drainBuildQueues(VillageAI village) {
        String blueprintId;
        while ((blueprintId = village.pollPendingQuickBlueprint()) != null) {
            quickBuildQueue.offer(new BuildQueueItem(blueprintId, village.blueprintService().isCriticalBuild(blueprintId)));
            journal.record("build_queued", village, null, "queue=quick blueprint=" + blueprintId);
        }
        while ((blueprintId = village.pollPendingLongBlueprint()) != null) {
            longBuildQueue.offer(new BuildQueueItem(blueprintId, village.blueprintService().isCriticalBuild(blueprintId)));
            journal.record("build_queued", village, null, "queue=long blueprint=" + blueprintId);
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
            syncPhysicalResources(village, "build_prepare");
            item.startedAtTick = currentTick;
            item.lastStepTick = currentTick;
            item.lastProgressTick = currentTick;
            item.status = TaskStatus.RUNNING;
            journal.record("build_started", village, null, "blueprint=" + item.blueprintId);
            var plan = service.prepareBuildPlan(item.blueprintId, village, null, village.center());
            if (plan.isEmpty()) {
                return handleBuildFailure(item, TaskStatus.RETRYABLE_FAILED, maxRetries, service);
            }
            item.plan = plan.get();
            item.totalSteps = item.plan.pendingSteps().size();
            if (item.totalSteps == 0) {
                item.status = TaskStatus.SUCCESS;
                item.finished = true;
                Bukkit.getPluginManager().callEvent(new VillageStructureBuiltEvent(village, item.blueprintId, 0L, item.placedSteps, 0));
                journal.record("build_completed", village, null, "blueprint=" + item.blueprintId + " placedSteps=0");
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
            Bukkit.getPluginManager().callEvent(new VillageStructureBuiltEvent(village, item.blueprintId, 0L, item.placedSteps, 0));
            journal.record("build_completed", village, null, "blueprint=" + item.blueprintId + " placedSteps=" + item.placedSteps);
        }

        return Math.max(1, processed);
    }

    private int handleBuildFailure(BuildQueueItem item, TaskStatus failureStatus, int maxRetries, BlueprintService service) {
        item.status = failureStatus;
        item.retries++;
        VillageAI village = villageManager.currentVillage().orElse(null);
        journal.record(
                "build_failed",
                village,
                null,
                "blueprint=" + item.blueprintId + " status=" + failureStatus + " retry=" + item.retries + "/" + maxRetries
        );

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

    private static final class TaskWatch {
        private final String taskId;
        private final String goalAtStart;
        private final String locationKey;
        private final String lastEvent;
        private int sameTicks;

        private TaskWatch(String taskId, String goalAtStart, String locationKey, String lastEvent) {
            this.taskId = taskId != null ? taskId : "unknown";
            this.goalAtStart = goalAtStart != null ? goalAtStart : "unknown";
            this.locationKey = locationKey != null ? locationKey : "unknown";
            this.lastEvent = lastEvent != null ? lastEvent : "unknown";
        }

        private static TaskWatch start(String taskId, String goalAtStart, String locationKey, String lastEvent) {
            return new TaskWatch(taskId, goalAtStart, locationKey, lastEvent);
        }
    }

    private static final class MissingEntityWatch {
        private final long firstSeenTick;
        private boolean reportedMissing;

        private MissingEntityWatch(long firstSeenTick) {
            this.firstSeenTick = Math.max(0L, firstSeenTick);
        }

        private long missingTicks(long currentTick) {
            return Math.max(0L, currentTick - firstSeenTick);
        }
    }

    private record AgentPlanValidation(boolean accepted, String reason) {
        private static AgentPlanValidation ok() {
            return new AgentPlanValidation(true, "ok");
        }

        private static AgentPlanValidation rejected(String reason) {
            return new AgentPlanValidation(false, reason != null ? reason : "unknown");
        }
    }

    private record AiGoalHold(AgentGoal goal, int priority, String reason, long expiresAtTick) {}

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
