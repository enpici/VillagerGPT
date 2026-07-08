package io.github.enpici.villager.life.village;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.blueprint.BuildingType;
import io.github.enpici.villager.life.event.VillageFoodLowEvent;
import io.github.enpici.villager.life.event.VillagerBornEvent;
import io.github.enpici.villager.life.role.AgentRole;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Villager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VillageAI {

    private final UUID id;
    private final String name;
    private final Location center;
    private final AgentManager agentManager;
    private final BlueprintService blueprintService;
    private final ResourceService resourceService;
    private final Queue<String> pendingQuickBlueprints = new ArrayDeque<>();
    private final Queue<String> pendingLongBlueprints = new ArrayDeque<>();
    private final ArrayDeque<MaterialRequest> pendingMaterialRequests = new ArrayDeque<>();
    private final Map<Material, Integer> materialStock = new ConcurrentHashMap<>();
    private final Map<Material, Integer> reservedMaterials = new ConcurrentHashMap<>();

    private int foodStock = 20;
    private int bedCount = 2;
    private int populationTarget = 6;
    private int maxPopulation = 30;
    private long lastThreatTick = -10_000L;
    private long lastReproductionTick = -10_000L;
    private long lastShelterPlanTick = -10_000L;
    private int shelterCount;
    private Map<Material, Integer> pendingMaterials = Map.of();
    private final Map<String, ThreatSignal> activeThreatSignals = new ConcurrentHashMap<>();

    public VillageAI(UUID id, String name, Location center, AgentManager agentManager, BlueprintService blueprintService) {
        this.id = id;
        this.name = name;
        this.center = center;
        this.agentManager = agentManager;
        this.blueprintService = blueprintService;
        this.resourceService = ResourceService.fromPluginConfig(this);
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public Location center() { return center.clone(); }
    public AgentManager agentManager() { return agentManager; }
    public BlueprintService blueprintService() { return blueprintService; }
    public ResourceService resourceService() { return resourceService; }

    public int population() { return agentManager.size(); }
    public int foodStock() { return foodStock; }
    public int bedCount() { return bedCount; }
    public int populationTarget() { return populationTarget; }
    public int maxPopulation() { return maxPopulation; }
    public boolean threatDetected() {
        pruneThreatSignals();
        return !activeThreatSignals.isEmpty() || currentTick() - lastThreatTick < 200L;
    }

    public Optional<Location> activeThreatLocation() {
        pruneThreatSignals();
        return activeThreatSignals.values().stream()
                .findFirst()
                .map(signal -> signal.location().clone());
    }

    public long lastShelterPlanTick() {
        return lastShelterPlanTick;
    }

    public int shelterCount() {
        return shelterCount;
    }

    public void markShelterPlanned() {
        try {
            lastShelterPlanTick = Bukkit.getCurrentTick();
        } catch (IllegalStateException | NullPointerException exception) {
            lastShelterPlanTick = 0L;
        }
        shelterCount++;
    }

    public void setFoodStock(int foodStock) {
        this.foodStock = Math.max(0, foodStock);
    }

    public void setBedCount(int bedCount) {
        this.bedCount = Math.max(0, bedCount);
    }

    public void setPopulationTarget(int populationTarget) {
        this.populationTarget = Math.max(0, populationTarget);
    }

    public void setMaxPopulation(int maxPopulation) {
        this.maxPopulation = Math.max(0, maxPopulation);
    }

    public synchronized void addFoodStock(int amount) {
        foodStock = Math.max(0, foodStock + amount);
    }

    public synchronized boolean consumeFood(int amount) {
        if (foodStock < amount) return false;
        foodStock -= amount;
        return true;
    }

    public void addMaterialStock(Material material, int amount) {
        if (material == null || amount <= 0) {
            return;
        }
        materialStock.merge(material, amount, Integer::sum);
    }

    public void removeMaterialStock(Material material, int amount) {
        if (material == null || amount <= 0) {
            return;
        }
        materialStock.compute(material, (m, current) -> {
            int next = (current == null ? 0 : current) - amount;
            return next > 0 ? next : null;
        });
    }

    public boolean replaceMaterialStock(Map<Material, Integer> physicalStock) {
        Map<Material, Integer> normalized = new ConcurrentHashMap<>();
        if (physicalStock != null) {
            physicalStock.forEach((material, amount) -> {
                if (material != null && amount != null && amount > 0) {
                    normalized.put(material, amount);
                }
            });
        }

        Map<Material, Integer> previous = materialStockSnapshot();
        materialStock.clear();
        materialStock.putAll(normalized);
        reservedMaterials.replaceAll((material, reserved) -> Math.min(reserved, materialStock.getOrDefault(material, 0)));
        reservedMaterials.entrySet().removeIf(entry -> entry.getValue() <= 0);
        return !previous.equals(materialStockSnapshot());
    }

    public boolean consumeMaterial(Material material, int amount) {
        if (material == null || amount <= 0) {
            return false;
        }
        int available = availableMaterial(material);
        if (available < amount) {
            return false;
        }
        materialStock.compute(material, (m, current) -> {
            int next = (current == null ? 0 : current) - amount;
            return next > 0 ? next : null;
        });
        return true;
    }

    public int totalMaterial(Material material) {
        return materialStock.getOrDefault(material, 0);
    }

    public int availableMaterial(Material material) {
        return Math.max(0, totalMaterial(material) - reservedMaterials.getOrDefault(material, 0));
    }

    public boolean reserveMaterial(Material material, int amount) {
        if (material == null || amount <= 0) {
            return false;
        }
        int available = availableMaterial(material);
        if (available < amount) {
            return false;
        }
        reservedMaterials.merge(material, amount, Integer::sum);
        return true;
    }

    public void releaseReservedMaterial(Material material, int amount) {
        if (material == null || amount <= 0) {
            return;
        }
        reservedMaterials.compute(material, (m, reserved) -> {
            int next = (reserved == null ? 0 : reserved) - amount;
            return next > 0 ? next : null;
        });
    }

    public boolean consumeReservedMaterial(Material material, int amount) {
        if (material == null || amount <= 0) {
            return false;
        }
        int reserved = reservedMaterials.getOrDefault(material, 0);
        int stock = materialStock.getOrDefault(material, 0);
        if (reserved < amount || stock < amount) {
            return false;
        }
        releaseReservedMaterial(material, amount);
        materialStock.compute(material, (m, current) -> {
            int next = (current == null ? 0 : current) - amount;
            return next > 0 ? next : null;
        });
        return true;
    }

    public Map<Material, Integer> materialStockSnapshot() {
        return Map.copyOf(materialStock);
    }

    public Map<Material, Integer> reservedMaterialsSnapshot() {
        return Map.copyOf(reservedMaterials);
    }

    public Map<Material, Integer> pendingMaterials() {
        return pendingMaterials;
    }

    public void setPendingMaterials(Map<Material, Integer> missingMaterials) {
        this.pendingMaterials = Map.copyOf(missingMaterials);
    }

    public synchronized void enqueueMaterialRequests(Map<Material, Integer> missingMaterials) {
        missingMaterials.forEach((material, amount) -> {
            if (amount <= 0) {
                return;
            }
            pendingMaterialRequests.offer(new MaterialRequest(material, amount));
        });
    }

    public synchronized void enqueuePriorityMaterialRequest(Material material, int amount) {
        if (material != null && amount > 0) {
            pendingMaterialRequests.addFirst(new MaterialRequest(material, amount));
        }
    }

    public synchronized MaterialRequest pollMaterialRequest() {
        return pendingMaterialRequests.poll();
    }

    public synchronized void requeueMaterialRequest(MaterialRequest request) {
        if (request != null && request.amount() > 0) {
            pendingMaterialRequests.offer(request);
        }
    }

    public boolean canReproduce() {
        if (population() >= maxPopulation || population() >= populationTarget) return false;
        if (foodStock < Math.max(8, population() * 2)) return false;
        if (bedCount < population() + 1) return false;
        if (threatDetected()) return false;
        return currentTick() - lastReproductionTick > 1_200L;
    }

    public void markReproduction() {
        lastReproductionTick = currentTick();
    }

    public void markThreat() {
        lastThreatTick = currentTick();
    }

    public void registerThreatSignal(String source, Location location, long ttlTicks) {
        if (source == null || source.isBlank()) {
            return;
        }
        long now = currentTick();
        long expiresAt = now + Math.max(1L, ttlTicks);
        Location threatLocation = location != null ? location.clone() : center.clone();
        activeThreatSignals.put(source, new ThreatSignal(threatLocation, expiresAt));
        lastThreatTick = now;
    }

    public void clearThreatSignal(String source) {
        if (source == null || source.isBlank()) {
            return;
        }
        activeThreatSignals.remove(source);
    }

    private void pruneThreatSignals() {
        long now = currentTick();
        Iterator<Map.Entry<String, ThreatSignal>> iterator = activeThreatSignals.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAtTick() <= now) {
                iterator.remove();
            }
        }
    }

    public Villager tryReproduce() {
        if (!canReproduce() || center.getWorld() == null) return null;
        Villager baby = center.getWorld().spawn(center, Villager.class);
        agentManager.registerChild(baby, AgentRole.FARMER, null, null);
        markReproduction();
        Bukkit.getPluginManager().callEvent(new VillagerBornEvent(this, baby));
        return baby;
    }

    public void planVillage() {
        if (foodStock < 10) {
            Bukkit.getPluginManager().callEvent(new VillageFoodLowEvent(this, foodStock));
            blueprintService.findFirstByType(BuildingType.FOOD_STORAGE)
                    .ifPresent(definition -> enqueueBlueprint(definition.id()));
        }

        if (bedCount < population()) {
            blueprintService.findFirstByType(BuildingType.HOUSE)
                    .map(definition -> definition.id())
                    .ifPresent(this::enqueueBlueprint);
        }
    }

    public String pollPendingQuickBlueprint() {
        return pendingQuickBlueprints.poll();
    }

    public String pollPendingLongBlueprint() {
        return pendingLongBlueprints.poll();
    }

    public void enqueueBlueprint(String blueprintId) {
        if (blueprintId == null || blueprintId.isBlank()) {
            return;
        }
        String normalized = blueprintId.toLowerCase();
        if (blueprintService.isLongBuild(normalized)) {
            pendingLongBlueprints.offer(normalized);
            return;
        }
        pendingQuickBlueprints.offer(normalized);
    }

    public int pendingBlueprintCount() {
        return pendingQuickBlueprints.size() + pendingLongBlueprints.size();
    }

    public List<String> pendingBlueprintsSnapshot(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> snapshot = new java.util.ArrayList<>(pendingQuickBlueprints);
        snapshot.addAll(pendingLongBlueprints);
        return snapshot.stream().limit(limit).toList();
    }

    public void ensureBasicNeedsForGrowth() {
        if (center.getWorld() != null) {
            return;
        }
        for (Agent agent : agentManager.all()) {
            if (agent.needLevel(io.github.enpici.villager.life.agent.NeedType.HUNGER) > 80) {
                foodStock = Math.max(foodStock, 15);
                return;
            }
        }
    }

    public record MaterialRequest(Material material, int amount) {}

    private long currentTick() {
        try {
            return Bukkit.getServer() != null ? Bukkit.getCurrentTick() : 0L;
        } catch (IllegalStateException | NullPointerException exception) {
            return 0L;
        }
    }

    private record ThreatSignal(Location location, long expiresAtTick) {}
}
