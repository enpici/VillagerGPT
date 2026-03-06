package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.VillagerLifePlugin;
import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.build.BlockPlacementStep;
import io.github.enpici.villager.life.build.BuildExecutor;
import io.github.enpici.villager.life.build.BuildPlan;
import io.github.enpici.villager.life.event.VillageStructureBuiltEvent;
import io.github.enpici.villager.life.integration.CitizensAdapter;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Villager;

import java.util.Optional;

public class BuildStructureTask extends BaseTask {

    private static final int MAX_STEP_REPLAN_ATTEMPTS = 1;

    private final String blueprintId;
    private BuildExecutor executor;
    private BuildPlan plan;
    private TaskState state = TaskState.PREPARE_PLAN;
    private long startedAtTick;
    private long nextPlacementTick;
    private int placedBlocks;
    private int failedBlocks;
    private int stepReplans;

    public BuildStructureTask(String blueprintId) {
        super("build_structure", 1200L);
        this.blueprintId = blueprintId;
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        if (!villageAI.blueprintService().hasBlueprint(blueprintId)) {
            return false;
        }
        Optional<BuildPlan> candidatePlan = villageAI.blueprintService().extractBuildPlan(blueprintId, villageAI.center());
        if (candidatePlan.isEmpty()) {
            return false;
        }
        boolean hasMaterials = villageAI.resourceService().hasMaterials(candidatePlan.get());
        if (!hasMaterials) {
            var missing = villageAI.resourceService().calculateMissingMaterials(candidatePlan.get());
            villageAI.setPendingMaterials(missing);
            villageAI.enqueueMaterialRequests(missing);
        }
        return hasMaterials;
    }

    @Override
    protected void onStart(Agent agent, VillageAI villageAI) {
        this.startedAtTick = Bukkit.getCurrentTick();
        Optional<BuildPlan> extractedPlan = villageAI.blueprintService().extractBuildPlan(blueprintId, villageAI.center());
        if (extractedPlan.isEmpty() || extractedPlan.get().size() == 0) {
            this.state = TaskState.FAILED;
            return;
        }
        this.plan = extractedPlan.get();
        if (!villageAI.resourceService().hasMaterials(plan)) {
            var missing = villageAI.resourceService().calculateMissingMaterials(plan);
            villageAI.setPendingMaterials(missing);
            villageAI.enqueueMaterialRequests(missing);
            this.state = TaskState.FAILED;
            return;
        }
        if (!villageAI.resourceService().reserveMaterials(plan)) {
            this.state = TaskState.FAILED;
            return;
        }
        villageAI.setPendingMaterials(java.util.Map.of());
        this.executor = new BuildExecutor(plan);
        this.state = TaskState.MOVE_TO_NEXT_BLOCK;
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        if (state == TaskState.FAILED) {
            return TaskStatus.FAILED;
        }
        if (executor == null) {
            return TaskStatus.FAILED;
        }
        if (executor.isFinished()) {
            emitCompletion(villageAI);
            return TaskStatus.SUCCESS;
        }

        BlockPlacementStep step = executor.currentStep();
        if (step == null) {
            emitCompletion(villageAI);
            return TaskStatus.SUCCESS;
        }

        if (!executor.arePrerequisitesSatisfied(step)) {
            executor.skipCurrent("Prerequisitos no satisfechos");
            failedBlocks++;
            return TaskStatus.RUNNING;
        }

        return switch (state) {
            case MOVE_TO_NEXT_BLOCK -> tickMoveToNextBlock(agent, step);
            case PLACE_BLOCK -> tickPlaceBlock(agent, villageAI, step);
            case VERIFY_PLACEMENT -> tickVerifyPlacement(step, villageAI);
            case PREPARE_PLAN -> TaskStatus.RUNNING;
            case FAILED -> TaskStatus.FAILED;
        };
    }

    private TaskStatus tickMoveToNextBlock(Agent agent, BlockPlacementStep step) {
        Villager villager = agent.resolveVillager();
        if (villager == null || villager.getWorld() == null) {
            state = TaskState.FAILED;
            return TaskStatus.FAILED;
        }

        Location target = step.position().clone().add(0.5, 0.0, 0.5);
        if (villager.getLocation().distanceSquared(target) <= 4.0) {
            state = TaskState.PLACE_BLOCK;
            return TaskStatus.RUNNING;
        }

        CitizensAdapter citizens = VillagerLifePlugin.instance().citizensAdapter();
        if (VillagerLifePlugin.instance().isCitizensIntegrationEnabled() && citizens != null) {
            NPC npc = citizens.getOrCreateNpc(villager);
            if (npc != null) {
                citizens.navigateTo(target);
            }
        } else {
            villager.teleport(target);
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus tickPlaceBlock(Agent agent, VillageAI villageAI, BlockPlacementStep step) {
        long now = Bukkit.getCurrentTick();
        int ticksPerBlock = Math.max(1, VillagerLifePlugin.instance().getConfig().getInt("build.granular.ticks-per-block", 5));
        if (nextPlacementTick == 0L) {
            nextPlacementTick = now + ticksPerBlock;
            return TaskStatus.RUNNING;
        }
        if (now < nextPlacementTick) {
            return TaskStatus.RUNNING;
        }

        CitizensAdapter citizens = VillagerLifePlugin.instance().citizensAdapter();
        if (citizens != null) {
            citizens.playSwingAnimation();
        }

        executor.markCurrentInProgress();
        step.incrementAttempts();
        boolean placed = villageAI.blueprintService().placeBlockAt(step);
        if (!placed && step.attempts() <= MAX_STEP_REPLAN_ATTEMPTS) {
            stepReplans++;
            nextPlacementTick = now + ticksPerBlock;
            state = TaskState.MOVE_TO_NEXT_BLOCK;
            return TaskStatus.RUNNING;
        }

        if (!placed) {
            executor.skipCurrent("No se pudo colocar bloque tras replanificar");
            failedBlocks++;
            nextPlacementTick = 0L;
            state = TaskState.MOVE_TO_NEXT_BLOCK;
            return TaskStatus.RUNNING;
        }

        state = TaskState.VERIFY_PLACEMENT;
        nextPlacementTick = 0L;
        return TaskStatus.RUNNING;
    }

    private TaskStatus tickVerifyPlacement(BlockPlacementStep step, VillageAI villageAI) {
        if (step.position().getBlock().getType() == step.material()) {
            if (!villageAI.resourceService().consumeForStep(step)) {
                executor.skipCurrent("Sin reserva de material para consumir");
                failedBlocks++;
                state = TaskState.MOVE_TO_NEXT_BLOCK;
                return TaskStatus.RUNNING;
            }
            executor.markCurrentPlaced();
            placedBlocks++;
        } else {
            executor.skipCurrent("Verificación fallida");
            failedBlocks++;
        }
        state = TaskState.MOVE_TO_NEXT_BLOCK;
        return TaskStatus.RUNNING;
    }


    @Override
    protected void onStop(Agent agent, VillageAI villageAI, TaskStatus reason) {
        if (reason != TaskStatus.SUCCESS && plan != null && executor != null) {
            villageAI.resourceService().releaseUnconsumed(plan, executor.currentIndex());
        }
    }

    private void emitCompletion(VillageAI villageAI) {
        long totalTicks = Math.max(0, Bukkit.getCurrentTick() - startedAtTick);
        Bukkit.getPluginManager().callEvent(new VillageStructureBuiltEvent(villageAI, blueprintId, totalTicks, placedBlocks, failedBlocks));
    }

    private enum TaskState {
        PREPARE_PLAN,
        MOVE_TO_NEXT_BLOCK,
        PLACE_BLOCK,
        VERIFY_PLACEMENT,
        FAILED
    }
}
