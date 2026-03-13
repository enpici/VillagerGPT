package io.github.enpici.villager.life.agent;

import io.github.enpici.villager.life.integration.CitizensGateway;
import io.github.enpici.villager.life.persistence.PersistenceListener;
import io.github.enpici.villager.life.role.AgentRole;
import net.citizensnpcs.api.npc.NPC;
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
    private PersistenceListener persistenceListener = PersistenceListener.NO_OP;

    public AgentManager(CitizensGateway citizensAdapter) {
        this.citizensAdapter = citizensAdapter;
    }

    public Agent register(Villager villager, AgentRole role) {
        Agent agent = agents.computeIfAbsent(villager.getUniqueId(), uuid -> new Agent(uuid, role));
        agent.setPersistenceListener(persistenceListener);
        agent.setRole(role);
        syncNpcMapping(villager, agent);
        persistenceListener.onAgentChanged(agent);
        return agent;
    }

    public Agent restore(UUID villagerUuid, AgentRole role, Integer npcId) {
        Agent agent = new Agent(villagerUuid, role);
        agent.setPersistenceListener(persistenceListener);
        agent.setNpcId(npcId);
        agents.put(villagerUuid, agent);
        if (npcId != null) {
            villagerToNpcId.put(villagerUuid, npcId);
        }
        persistenceListener.onAgentChanged(agent);
        return agent;
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
        boolean removed = agents.remove(uuid) != null;
        if (removed) {
            persistenceListener.onAgentRemoved(uuid);
        }
        return removed;
    }

    public void clearAllForRestore() {
        agents.clear();
        villagerToNpcId.clear();
    }

    public void setPersistenceListener(PersistenceListener persistenceListener) {
        this.persistenceListener = persistenceListener == null ? PersistenceListener.NO_OP : persistenceListener;
        for (Agent agent : agents.values()) {
            agent.setPersistenceListener(this.persistenceListener);
        }
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
}
