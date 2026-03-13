package io.github.enpici.villager.life.persistence;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.village.VillageAI;

import java.util.UUID;

public interface PersistenceListener {

    PersistenceListener NO_OP = new PersistenceListener() {};

    default void onVillageChanged(VillageAI village) {
    }

    default void onAgentChanged(Agent agent) {
    }

    default void onAgentRemoved(UUID agentId) {
    }
}
