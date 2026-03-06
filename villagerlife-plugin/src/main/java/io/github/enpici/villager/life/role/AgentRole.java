package io.github.enpici.villager.life.role;

public enum AgentRole {
    FARMER(1.0, 0.2, 0.4),
    BUILDER(0.4, 1.0, 0.3),
    GUARD(0.2, 0.2, 1.0),
    MINER(0.6, 0.6, 0.4),
    TRADER(0.5, 0.1, 0.2),
    LEADER(0.5, 0.5, 0.5),
    CRAFTER(0.7, 0.7, 0.3);

    private final double foodPriority;
    private final double buildPriority;
    private final double defensePriority;

    AgentRole(double foodPriority, double buildPriority, double defensePriority) {
        this.foodPriority = foodPriority;
        this.buildPriority = buildPriority;
        this.defensePriority = defensePriority;
    }

    public double foodPriority() {
        return foodPriority;
    }

    public double buildPriority() {
        return buildPriority;
    }

    public double defensePriority() {
        return defensePriority;
    }
}
