package io.github.enpici.villager.life.agent;

import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.task.Task;
import io.github.enpici.villager.life.task.TaskStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Agent {

    private final UUID villagerUuid;
    private final EnumMap<NeedType, Double> needs = new EnumMap<>(NeedType.class);
    private final Map<UUID, Integer> relationships = new ConcurrentHashMap<>();
    private AgentRole role;
    private Task activeTask;
    private String lastEvent = "spawned";
    private Integer npcId;

    public Agent(UUID villagerUuid, AgentRole role) {
        this.villagerUuid = villagerUuid;
        this.role = role;
        for (NeedType type : NeedType.values()) {
            needs.put(type, 20d);
        }
    }

    public UUID villagerUuid() {
        return villagerUuid;
    }

    public AgentRole role() {
        return role;
    }

    public void setRole(AgentRole role) {
        this.role = role;
    }

    public double needLevel(NeedType type) {
        return needs.getOrDefault(type, 0d);
    }

    public Map<NeedType, Double> needsSnapshot() {
        return Map.copyOf(needs);
    }

    public void decayNeeds() {
        adjustNeed(NeedType.HUNGER, 0.8);
        adjustNeed(NeedType.ENERGY, 0.6);
        adjustNeed(NeedType.SAFETY, 0.2);
        adjustNeed(NeedType.SOCIAL, 0.5);
    }

    public void adjustNeed(NeedType type, double delta) {
        double value = Math.max(0, Math.min(100, needLevel(type) + delta));
        needs.put(type, value);
    }

    public Task activeTask() {
        return activeTask;
    }

    public void assignTask(Task task) {
        this.activeTask = task;
    }

    public void clearTask(TaskStatus reason) {
        this.activeTask = null;
        this.lastEvent = "task:" + reason.name().toLowerCase();
    }

    public String lastEvent() {
        return lastEvent;
    }

    public void setLastEvent(String lastEvent) {
        this.lastEvent = lastEvent;
    }

    public Integer npcId() {
        return npcId;
    }

    public void setNpcId(Integer npcId) {
        this.npcId = npcId;
    }

    public Map<UUID, Integer> relationshipsSnapshot() {
        return Map.copyOf(relationships);
    }

    public void adjustRelationship(Player player, int delta) {
        relationships.merge(player.getUniqueId(), delta, Integer::sum);
    }

    public Villager resolveVillager() {
        var entity = Bukkit.getEntity(villagerUuid);
        return entity instanceof Villager villager ? villager : null;
    }
}
