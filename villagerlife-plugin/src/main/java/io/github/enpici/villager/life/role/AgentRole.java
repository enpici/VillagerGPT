package io.github.enpici.villager.life.role;

public enum AgentRole {
    FARMER(1.0, 0.2, 0.4, "farms food and basic natural materials"),
    BUILDER(0.4, 1.0, 0.3, "turns plans and stored materials into structures"),
    GUARD(0.2, 0.2, 1.0, "patrols, reacts to threats, and keeps villagers safe"),
    MINER(0.3, 0.8, 0.5, "gathers stone, ores, coal, and underground materials"),
    TRADER(0.7, 0.2, 0.2, "exchanges surplus for food, emeralds, and missing goods"),
    LEADER(0.5, 0.6, 0.6, "reads village state and pushes the colony toward stability"),
    CRAFTER(0.4, 0.9, 0.3, "converts raw materials into build-ready supplies");

    private final double foodPriority;
    private final double buildPriority;
    private final double defensePriority;
    private final String purpose;

    AgentRole(double foodPriority, double buildPriority, double defensePriority, String purpose) {
        this.foodPriority = foodPriority;
        this.buildPriority = buildPriority;
        this.defensePriority = defensePriority;
        this.purpose = purpose;
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

    public String purpose() {
        return purpose;
    }
}
