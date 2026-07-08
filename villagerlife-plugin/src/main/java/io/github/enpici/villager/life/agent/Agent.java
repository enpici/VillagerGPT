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
    private LifeStage lifeStage = LifeStage.ADULT;
    private long ageTicks;
    private int generation;
    private UUID parentA;
    private UUID parentB;
    private UUID partner;
    private long createdAtMs = System.currentTimeMillis();
    private long lastReproductionTick = -10_000L;
    private String lastDecisionReason = "spawned";
    private AgentGoal currentGoal = AgentGoal.IDLE;
    private String currentGoalReason = "spawned";
    private int currentGoalPriority;
    private long currentGoalStartedTick;
    private boolean missingEntity;
    private final EnumMap<AgentRole, Integer> skillXpByRole = new EnumMap<>(AgentRole.class);

    public Agent(UUID villagerUuid, AgentRole role) {
        this.villagerUuid = villagerUuid;
        this.role = role;
        for (NeedType type : NeedType.values()) {
            needs.put(type, 20d);
        }
        for (AgentRole agentRole : AgentRole.values()) {
            skillXpByRole.put(agentRole, 0);
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

    public LifeStage lifeStage() {
        return lifeStage;
    }

    public void setLifeStage(LifeStage lifeStage) {
        this.lifeStage = lifeStage != null ? lifeStage : LifeStage.ADULT;
    }

    public long ageTicks() {
        return ageTicks;
    }

    public void setAgeTicks(long ageTicks) {
        this.ageTicks = Math.max(0L, ageTicks);
    }

    public boolean tickAge(long adultAgeTicks) {
        ageTicks++;
        if (lifeStage == LifeStage.CHILD && ageTicks >= adultAgeTicks) {
            lifeStage = LifeStage.ADULT;
            lastEvent = "life:adult";
            return true;
        }
        return false;
    }

    public boolean isAdult() {
        return lifeStage == LifeStage.ADULT;
    }

    public int generation() {
        return generation;
    }

    public void setGeneration(int generation) {
        this.generation = Math.max(0, generation);
    }

    public UUID parentA() {
        return parentA;
    }

    public UUID parentB() {
        return parentB;
    }

    public void setParents(UUID parentA, UUID parentB) {
        this.parentA = parentA;
        this.parentB = parentB;
    }

    public UUID partner() {
        return partner;
    }

    public void setPartner(UUID partner) {
        this.partner = partner;
    }

    public long createdAtMs() {
        return createdAtMs;
    }

    public void setCreatedAtMs(long createdAtMs) {
        this.createdAtMs = Math.max(0L, createdAtMs);
    }

    public long lastReproductionTick() {
        return lastReproductionTick;
    }

    public void setLastReproductionTick(long lastReproductionTick) {
        this.lastReproductionTick = lastReproductionTick;
    }

    public String lastDecisionReason() {
        return lastDecisionReason;
    }

    public void setLastDecisionReason(String lastDecisionReason) {
        this.lastDecisionReason = lastDecisionReason != null && !lastDecisionReason.isBlank()
                ? lastDecisionReason
                : "unknown";
    }

    public AgentGoal currentGoal() {
        return currentGoal;
    }

    public String currentGoalReason() {
        return currentGoalReason;
    }

    public int currentGoalPriority() {
        return currentGoalPriority;
    }

    public long currentGoalStartedTick() {
        return currentGoalStartedTick;
    }

    public boolean hasGoal(AgentGoal goal) {
        return currentGoal == goal;
    }

    public boolean assignGoal(AgentGoal goal, int priority, String reason, long currentTick) {
        AgentGoal next = goal != null ? goal : AgentGoal.IDLE;
        int clampedPriority = Math.max(0, Math.min(100, priority));
        String cleanReason = reason != null && !reason.isBlank() ? reason : "unknown";
        boolean changed = currentGoal != next
                || currentGoalPriority != clampedPriority
                || !currentGoalReason.equals(cleanReason);
        if (changed) {
            currentGoal = next;
            currentGoalPriority = clampedPriority;
            currentGoalReason = cleanReason;
            currentGoalStartedTick = Math.max(0L, currentTick);
            lastEvent = "goal:" + next.name().toLowerCase();
        }
        return changed;
    }

    public boolean missingEntity() {
        return missingEntity;
    }

    public void setMissingEntity(boolean missingEntity) {
        this.missingEntity = missingEntity;
    }

    public Map<AgentRole, Integer> skillXpSnapshot() {
        return Map.copyOf(skillXpByRole);
    }

    public int skillXp(AgentRole role) {
        return skillXpByRole.getOrDefault(role, 0);
    }

    public void setSkillXp(AgentRole role, int xp) {
        if (role != null) {
            skillXpByRole.put(role, Math.max(0, xp));
        }
    }

    public void addSkillXp(AgentRole role, int xp) {
        if (role != null && xp > 0) {
            skillXpByRole.merge(role, xp, Integer::sum);
        }
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

    public void setNeed(NeedType type, double value) {
        if (type != null) {
            needs.put(type, Math.max(0, Math.min(100, value)));
        }
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

    public void setRelationship(UUID playerUuid, int value) {
        if (playerUuid != null) {
            relationships.put(playerUuid, value);
        }
    }

    public Villager resolveVillager() {
        var entity = Bukkit.getEntity(villagerUuid);
        if (entity instanceof Villager villager) {
            missingEntity = false;
            return villager;
        }
        missingEntity = true;
        return null;
    }
}
