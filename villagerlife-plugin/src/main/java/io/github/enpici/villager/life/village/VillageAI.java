package io.github.enpici.villager.life.village;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.event.VillageFoodLowEvent;
import io.github.enpici.villager.life.event.VillagerBornEvent;
import io.github.enpici.villager.life.role.AgentRole;
import org.bukkit.entity.Villager;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

public class VillageAI {

    private final UUID id;
    private final String name;
    private final Location center;
    private final AgentManager agentManager;
    private final BlueprintService blueprintService;
    private final Queue<String> pendingBlueprints = new ArrayDeque<>();

    private int foodStock = 20;
    private int bedCount = 2;
    private int populationTarget = 6;
    private int maxPopulation = 30;
    private long lastThreatTick = -10_000L;
    private long lastReproductionTick = -10_000L;

    public VillageAI(UUID id, String name, Location center, AgentManager agentManager, BlueprintService blueprintService) {
        this.id = id;
        this.name = name;
        this.center = center;
        this.agentManager = agentManager;
        this.blueprintService = blueprintService;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public Location center() { return center.clone(); }
    public AgentManager agentManager() { return agentManager; }
    public BlueprintService blueprintService() { return blueprintService; }

    public int population() { return agentManager.size(); }
    public int foodStock() { return foodStock; }
    public int bedCount() { return bedCount; }
    public int populationTarget() { return populationTarget; }
    public boolean threatDetected() { return Bukkit.getCurrentTick() - lastThreatTick < 200L; }

    public void addFoodStock(int amount) {
        foodStock = Math.max(0, foodStock + amount);
    }

    public boolean consumeFood(int amount) {
        if (foodStock < amount) return false;
        foodStock -= amount;
        return true;
    }

    public boolean canReproduce() {
        if (population() >= maxPopulation || population() >= populationTarget) return false;
        if (foodStock < Math.max(8, population() * 2)) return false;
        if (bedCount < population() + 1) return false;
        if (threatDetected()) return false;
        return Bukkit.getCurrentTick() - lastReproductionTick > 1_200L;
    }

    public void markReproduction() {
        lastReproductionTick = Bukkit.getCurrentTick();
    }

    public void markThreat() {
        lastThreatTick = Bukkit.getCurrentTick();
    }

    public Villager tryReproduce() {
        if (!canReproduce() || center.getWorld() == null) return null;
        Villager baby = center.getWorld().spawn(center, Villager.class);
        agentManager.register(baby, AgentRole.FARMER);
        markReproduction();
        Bukkit.getPluginManager().callEvent(new VillagerBornEvent(this, baby));
        return baby;
    }

    public void planVillage() {
        if (foodStock < 10) {
            Bukkit.getPluginManager().callEvent(new VillageFoodLowEvent(this, foodStock));
        }

        if (bedCount < population()) {
            pendingBlueprints.offer("house_small");
        }
    }

    public String pollPendingBlueprint() {
        return pendingBlueprints.poll();
    }

    public void enqueueBlueprint(String blueprintId) {
        pendingBlueprints.offer(blueprintId.toLowerCase());
    }

    public void ensureBasicNeedsForGrowth() {
        for (Agent agent : agentManager.all()) {
            if (agent.needLevel(io.github.enpici.villager.life.agent.NeedType.HUNGER) > 80) {
                foodStock = Math.max(foodStock, 15);
                return;
            }
        }
    }
}
