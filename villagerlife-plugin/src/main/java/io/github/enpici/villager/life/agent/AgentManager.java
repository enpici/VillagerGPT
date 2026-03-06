package io.github.enpici.villager.life.agent;

import io.github.enpici.villager.life.role.AgentRole;
import org.bukkit.entity.Villager;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AgentManager {

    private final Map<UUID, Agent> agents = new ConcurrentHashMap<>();

    public Agent register(Villager villager, AgentRole role) {
        return agents.computeIfAbsent(villager.getUniqueId(), uuid -> new Agent(uuid, role));
    }

    public Optional<Agent> find(UUID uuid) {
        return Optional.ofNullable(agents.get(uuid));
    }

    public Optional<Agent> find(Villager villager) {
        return find(villager.getUniqueId());
    }

    public Collection<Agent> all() {
        return agents.values();
    }

    public int size() {
        return agents.size();
    }

    public boolean unregister(UUID uuid) {
        return agents.remove(uuid) != null;
    }
}
