package io.github.enpici.villager.life.agent;

import io.github.enpici.villager.life.integration.CitizensGateway;
import io.github.enpici.villager.life.role.AgentRole;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Villager;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AgentManager {

    private final Map<UUID, Agent> agents = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> villagerToNpcId = new ConcurrentHashMap<>();
    private final CitizensGateway citizensAdapter;

    public AgentManager(CitizensGateway citizensAdapter) {
        this.citizensAdapter = citizensAdapter;
    }

    public Agent register(Villager villager, AgentRole role) {
        Agent agent = agents.computeIfAbsent(villager.getUniqueId(), uuid -> new Agent(uuid, role));
        agent.setRole(role);
        agent.setMissingEntity(false);
        syncVisualAge(agent, villager);
        syncNpcMapping(villager, agent);
        return agent;
    }

    public Agent registerChild(Villager villager, AgentRole role, Agent parentA, Agent parentB) {
        Agent agent = register(villager, role);
        agent.setLifeStage(LifeStage.CHILD);
        agent.setAgeTicks(0L);
        int generation = Math.max(
                parentA != null ? parentA.generation() : 0,
                parentB != null ? parentB.generation() : 0
        ) + 1;
        agent.setGeneration(generation);
        agent.setParents(parentA != null ? parentA.villagerUuid() : null, parentB != null ? parentB.villagerUuid() : null);
        agent.setLastEvent("life:born");
        agent.setLastDecisionReason("born into village");
        syncVisualAge(agent, villager);
        return agent;
    }

    public void restore(Agent agent) {
        agents.put(agent.villagerUuid(), agent);
        Villager villager;
        try {
            villager = agent.resolveVillager();
        } catch (IllegalStateException | NullPointerException exception) {
            agent.setMissingEntity(true);
            return;
        }
        if (villager != null) {
            syncVisualAge(agent, villager);
            syncNpcMapping(villager, agent);
        }
    }

    public void syncVisualAge(Agent agent) {
        if (agent == null) {
            return;
        }
        Villager villager;
        try {
            villager = agent.resolveVillager();
        } catch (IllegalStateException | NullPointerException exception) {
            agent.setMissingEntity(true);
            return;
        }
        if (villager != null) {
            syncVisualAge(agent, villager);
        }
    }

    public Optional<Agent> find(UUID uuid) {
        return Optional.ofNullable(agents.get(uuid));
    }

    public Optional<Agent> find(Villager villager) {
        return find(villager.getUniqueId());
    }

    public Optional<Integer> findNpcId(UUID villagerUuid) {
        return Optional.ofNullable(villagerToNpcId.get(villagerUuid));
    }

    public Collection<Agent> all() {
        return agents.values();
    }

    public int size() {
        return agents.size();
    }

    public boolean unregister(UUID uuid) {
        villagerToNpcId.remove(uuid);
        return agents.remove(uuid) != null;
    }

    private void syncNpcMapping(Villager villager, Agent agent) {
        if (citizensAdapter == null || !citizensAdapter.isAvailable()) {
            villagerToNpcId.remove(agent.villagerUuid());
            agent.setNpcId(null);
            return;
        }

        NPC npc = citizensAdapter.getOrCreateNpc(villager);
        if (npc == null) {
            villagerToNpcId.remove(agent.villagerUuid());
            agent.setNpcId(null);
            return;
        }

        villagerToNpcId.put(agent.villagerUuid(), npc.getId());
        agent.setNpcId(npc.getId());
    }

    private void syncVisualAge(Agent agent, Villager villager) {
        if (!(villager instanceof Ageable ageable)) {
            return;
        }
        if (agent.lifeStage() == LifeStage.CHILD) {
            ageable.setBaby();
            ageable.setAgeLock(true);
            return;
        }
        ageable.setAdult();
        ageable.setAgeLock(false);
    }
}
