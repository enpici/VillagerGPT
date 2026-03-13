package io.github.enpici.villager.life.village;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.blueprint.BuildingType;
import io.github.enpici.villager.life.event.VillageFoodLowEvent;
import io.github.enpici.villager.life.event.VillagerBornEvent;
import io.github.enpici.villager.life.persistence.PersistenceListener;
import io.github.enpici.villager.life.role.AgentRole;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Villager;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
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
    private final Queue<MaterialRequest> pendingMaterialRequests = new ArrayDeque<>();
    private final Map<Material, Integer> materialStock = new ConcurrentHashMap<>();
    private final Map<Material, Integer> reservedMaterials = new ConcurrentHashMap<>();

    private int foodStock = 20;
    private int bedCount = 2;
    private int populationTarget = 6;
    private int maxPopulation = 30;
    private long lastThreatTick = -10_000L;
    private long lastReproductionTick = -10_000L;
    private Map<Material, Integer> pendingMaterials = Map.of();
    private PersistenceListener persistenceListener = PersistenceListener.NO_OP;

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
    public long lastThreatTick() { return lastThreatTick; }
    public long lastReproductionTick() { return lastReproductionTick; }
    public boolean threatDetected() { return Bukkit.getCurrentTick() - lastThreatTick < 200L; }

    public synchronized void addFoodStock(int amount) {
        foodStock = Math.max(0, foodStock + amount);
        persistenceListener.onVillageChanged(this);
    }

    public synchronized boolean consumeFood(int amount) {
        if (foodStock < amount) return false;
        foodStock -= amount;
        persistenceListener.onVillageChanged(this);
        return true;
    }

    public void addMaterialStock(Material material, int amount) {
        if (material == null || amount <= 0) {
            return;
        }
        materialStock.merge(material, amount, Integer::sum);
        persistenceListener.onVillageChanged(this);
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
        persistenceListener.onVillageChanged(this);
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
        persistenceListener.onVillageChanged(this);
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
        persistenceListener.onVillageChanged(this);
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
        persistenceListener.onVillageChanged(this);
    }

    public void enqueueMaterialRequests(Map<Material, Integer> missingMaterials) {
        missingMaterials.forEach((material, amount) -> {
            if (amount <= 0) {
                return;
            }
            pendingMaterialRequests.offer(new MaterialRequest(material, amount));
        });
        persistenceListener.onVillageChanged(this);
    }

    public MaterialRequest pollMaterialRequest() {
        MaterialRequest request = pendingMaterialRequests.poll();
        if (request != null) {
            persistenceListener.onVillageChanged(this);
        }
        return request;
    }

    public void requeueMaterialRequest(MaterialRequest request) {
        if (request != null && request.amount() > 0) {
            pendingMaterialRequests.offer(request);
            persistenceListener.onVillageChanged(this);
        }
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
        persistenceListener.onVillageChanged(this);
    }

    public void markThreat() {
        lastThreatTick = Bukkit.getCurrentTick();
        persistenceListener.onVillageChanged(this);
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
            blueprintService.findFirstByType(BuildingType.FOOD_STORAGE)
                    .ifPresent(definition -> enqueueBlueprint(definition.id()));
        }

        if (bedCount < population()) {
            String houseBlueprint = blueprintService.findFirstByType(BuildingType.HOUSE)
                    .map(definition -> definition.id())
                    .orElse("house_small");
            enqueueBlueprint(houseBlueprint);
        }
    }

    public String pollPendingQuickBlueprint() {
        String item = pendingQuickBlueprints.poll();
        if (item != null) {
            persistenceListener.onVillageChanged(this);
        }
        return item;
    }

    public String pollPendingLongBlueprint() {
        String item = pendingLongBlueprints.poll();
        if (item != null) {
            persistenceListener.onVillageChanged(this);
        }
        return item;
    }

    public void enqueueBlueprint(String blueprintId) {
        if (blueprintId == null || blueprintId.isBlank()) {
            return;
        }
        String normalized = blueprintId.toLowerCase();
        if (blueprintService.isLongBuild(normalized)) {
            pendingLongBlueprints.offer(normalized);
            persistenceListener.onVillageChanged(this);
            return;
        }
        pendingQuickBlueprints.offer(normalized);
        persistenceListener.onVillageChanged(this);
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

    public List<String> pendingQuickBlueprintsSnapshot() {
        return List.copyOf(pendingQuickBlueprints);
    }

    public List<String> pendingLongBlueprintsSnapshot() {
        return List.copyOf(pendingLongBlueprints);
    }

    public List<MaterialRequest> pendingMaterialRequestsSnapshot() {
        return List.copyOf(pendingMaterialRequests);
    }

    public void ensureBasicNeedsForGrowth() {
        for (Agent agent : agentManager.all()) {
            if (agent.needLevel(io.github.enpici.villager.life.agent.NeedType.HUNGER) > 80) {
                foodStock = Math.max(foodStock, 15);
                persistenceListener.onVillageChanged(this);
                return;
            }
        }
    }

    public void setStateForRestore(int foodStock,
                                   int bedCount,
                                   int populationTarget,
                                   int maxPopulation,
                                   long lastThreatTick,
                                   long lastReproductionTick,
                                   Map<Material, Integer> materialStock,
                                   Map<Material, Integer> reservedMaterials,
                                   Map<Material, Integer> pendingMaterials,
                                   List<String> pendingQuickBlueprints,
                                   List<String> pendingLongBlueprints,
                                   List<MaterialRequest> pendingMaterialRequests) {
        this.foodStock = foodStock;
        this.bedCount = bedCount;
        this.populationTarget = populationTarget;
        this.maxPopulation = maxPopulation;
        this.lastThreatTick = lastThreatTick;
        this.lastReproductionTick = lastReproductionTick;
        this.materialStock.clear();
        this.materialStock.putAll(materialStock);
        this.reservedMaterials.clear();
        this.reservedMaterials.putAll(reservedMaterials);
        this.pendingMaterials = Map.copyOf(pendingMaterials);
        this.pendingQuickBlueprints.clear();
        this.pendingQuickBlueprints.addAll(pendingQuickBlueprints);
        this.pendingLongBlueprints.clear();
        this.pendingLongBlueprints.addAll(pendingLongBlueprints);
        this.pendingMaterialRequests.clear();
        this.pendingMaterialRequests.addAll(pendingMaterialRequests);
    }

    public void setPersistenceListener(PersistenceListener persistenceListener) {
        this.persistenceListener = persistenceListener == null ? PersistenceListener.NO_OP : persistenceListener;
    }

    public record MaterialRequest(Material material, int amount) {}
}
