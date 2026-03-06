package io.github.enpici.villager.life.build;

import java.util.ArrayList;
import java.util.List;

public class BuildPlan {

    private final List<BlockPlacementStep> steps;

    public BuildPlan(List<BlockPlacementStep> steps) {
        this.steps = new ArrayList<>(steps);
    }

    public List<BlockPlacementStep> steps() {
        return List.copyOf(steps);
    }

    public int size() {
        return steps.size();
    }

    public BlockPlacementStep get(int index) {
        return steps.get(index);
    }
}
