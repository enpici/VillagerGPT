package io.github.enpici.villager.life.build;

import java.util.List;

public class BuildExecutor {

    private final BuildPlan plan;
    private int cursor;

    public BuildExecutor(BuildPlan plan) {
        this.plan = plan;
    }

    public BlockPlacementStep currentStep() {
        if (cursor >= plan.size()) {
            return null;
        }
        return plan.get(cursor);
    }

    public int currentIndex() {
        return cursor;
    }

    public boolean isFinished() {
        return cursor >= plan.size();
    }

    public boolean arePrerequisitesSatisfied(BlockPlacementStep step) {
        List<Integer> prerequisites = step.prerequisiteIndices();
        for (Integer index : prerequisites) {
            if (index == null || index < 0 || index >= plan.size()) {
                return false;
            }
            BlockPlacementStep prerequisite = plan.get(index);
            if (prerequisite.status() != BlockPlacementStep.StepStatus.PLACED
                    && prerequisite.status() != BlockPlacementStep.StepStatus.SKIPPED) {
                return false;
            }
        }
        return true;
    }

    public void markCurrentInProgress() {
        BlockPlacementStep current = currentStep();
        if (current != null) {
            current.markStatus(BlockPlacementStep.StepStatus.IN_PROGRESS, null);
        }
    }

    public void markCurrentPlaced() {
        BlockPlacementStep current = currentStep();
        if (current != null) {
            current.markStatus(BlockPlacementStep.StepStatus.PLACED, null);
            cursor++;
        }
    }

    public void skipCurrent(String reason) {
        BlockPlacementStep current = currentStep();
        if (current != null) {
            current.markStatus(BlockPlacementStep.StepStatus.SKIPPED, reason);
            cursor++;
        }
    }
}
