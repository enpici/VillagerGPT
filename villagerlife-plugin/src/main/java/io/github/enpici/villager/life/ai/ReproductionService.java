package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.village.PhysicalResourceScanner;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;

public class ReproductionService {

    private final JavaPlugin plugin;
    private final PhysicalResourceScanner physicalResourceScanner = new PhysicalResourceScanner();

    public ReproductionService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Villager tryReproduce(VillageAI village) {
        Agent first = village.agentManager().all().stream()
                .filter(agent -> canParent(agent, village))
                .min(Comparator.comparingLong(Agent::lastReproductionTick))
                .orElse(null);
        if (first == null) {
            return null;
        }

        Agent second = village.agentManager().all().stream()
                .filter(agent -> !agent.villagerUuid().equals(first.villagerUuid()))
                .filter(agent -> canParent(agent, village))
                .filter(agent -> compatible(first, agent))
                .filter(agent -> closeEnough(first, agent))
                .min(Comparator.comparingLong(Agent::lastReproductionTick))
                .orElse(null);
        if (second == null || village.center().getWorld() == null) {
            return null;
        }

        int birthFoodCost = Math.max(1, config().getInt("reproduction.birth-food-cost", 4));
        int consumedFood = physicalResourceScanner.consumeFood(village, village.agentManager().all(), first, birthFoodCost, resourceScanRadius());
        if (consumedFood < birthFoodCost) {
            return null;
        }

        Location spawnLocation = birthLocation(first, second, village);
        Villager baby = spawnLocation.getWorld().spawn(spawnLocation, Villager.class);
        Agent child = village.agentManager().registerChild(baby, AgentRole.FARMER, first, second);

        long now = Bukkit.getCurrentTick();
        first.setPartner(second.villagerUuid());
        second.setPartner(first.villagerUuid());
        first.setLastReproductionTick(now);
        second.setLastReproductionTick(now);
        first.setLastEvent("life:parent");
        second.setLastEvent("life:parent");
        child.setLastDecisionReason("born to " + first.villagerUuid() + " and " + second.villagerUuid());
        village.markReproduction();
        Bukkit.getPluginManager().callEvent(new io.github.enpici.villager.life.event.VillagerBornEvent(village, baby));
        return baby;
    }

    public boolean canParent(Agent agent, VillageAI village) {
        if (!agent.isAdult() || agent.missingEntity()) return false;
        if (village.population() >= village.maxPopulation() || village.population() >= village.populationTarget()) return false;
        if (village.bedCount() < village.population() + 1) return false;
        if (village.threatDetected()) return false;
        if (village.foodStock() < minFood(village)) return false;
        long cooldown = Math.max(1L, config().getLong("reproduction.cooldown-ticks", 1_200L));
        return Bukkit.getCurrentTick() - agent.lastReproductionTick() >= cooldown;
    }

    private boolean compatible(Agent first, Agent second) {
        if (first.partner() == null && second.partner() == null) return true;
        return first.villagerUuid().equals(second.partner()) || second.villagerUuid().equals(first.partner());
    }

    private boolean closeEnough(Agent first, Agent second) {
        Villager firstVillager = first.resolveVillager();
        Villager secondVillager = second.resolveVillager();
        if (firstVillager == null || secondVillager == null) return false;
        if (firstVillager.getWorld() != secondVillager.getWorld()) return false;
        double radius = Math.max(1.0, config().getDouble("reproduction.partner-radius", 12.0));
        return firstVillager.getLocation().distanceSquared(secondVillager.getLocation()) <= radius * radius;
    }

    private Location birthLocation(Agent first, Agent second, VillageAI village) {
        Villager firstVillager = first.resolveVillager();
        Villager secondVillager = second.resolveVillager();
        if (firstVillager != null && secondVillager != null && firstVillager.getWorld() == secondVillager.getWorld()) {
            return firstVillager.getLocation().add(secondVillager.getLocation()).multiply(0.5);
        }
        return village.center();
    }

    private int minFood(VillageAI village) {
        int configured = config().getInt("reproduction.min-food-stock", 8);
        return Math.max(configured, village.population() * 2);
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    private int resourceScanRadius() {
        return Math.max(1, config().getInt("village.resource-scan-radius",
                config().getInt("build.nearby-container-radius", 24)));
    }
}
