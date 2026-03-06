package io.github.enpici.villager.life.build;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.List;

public class BlockPlacementStep {

    private final Material material;
    private final Location position;
    private final List<Integer> prerequisiteIndices;
    private int attempts;
    private StepStatus status;
    private String statusReason;

    public BlockPlacementStep(Material material, Location position, List<Integer> prerequisiteIndices) {
        this.material = material;
        this.position = position;
        this.prerequisiteIndices = List.copyOf(prerequisiteIndices);
        this.status = StepStatus.PENDING;
    }

    public Material material() {
        return material;
    }

    public Location position() {
        return position.clone();
    }

    public List<Integer> prerequisiteIndices() {
        return prerequisiteIndices;
    }

    public int attempts() {
        return attempts;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public StepStatus status() {
        return status;
    }

    public String statusReason() {
        return statusReason;
    }

    public void markStatus(StepStatus status, String statusReason) {
        this.status = status;
        this.statusReason = statusReason;
    }

    public enum StepStatus {
        PENDING,
        IN_PROGRESS,
        PLACED,
        SKIPPED
    }
}
