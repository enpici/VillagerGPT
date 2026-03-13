package io.github.enpici.villager.life.village;

import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.persistence.PersistenceListener;
import org.bukkit.Location;

import java.util.Optional;
import java.util.UUID;

public class VillageManager {

    private VillageAI currentVillage;
    private PersistenceListener persistenceListener = PersistenceListener.NO_OP;

    public VillageAI createVillage(String name, Location center, AgentManager agentManager, BlueprintService blueprintService) {
        return createVillage(UUID.randomUUID(), name, center, agentManager, blueprintService);
    }

    public VillageAI createVillage(UUID id, String name, Location center, AgentManager agentManager, BlueprintService blueprintService) {
        this.currentVillage = new VillageAI(id, name, center, agentManager, blueprintService);
        this.currentVillage.setPersistenceListener(persistenceListener);
        return currentVillage;
    }

    public void setPersistenceListener(PersistenceListener persistenceListener) {
        this.persistenceListener = persistenceListener == null ? PersistenceListener.NO_OP : persistenceListener;
        if (currentVillage != null) {
            currentVillage.setPersistenceListener(this.persistenceListener);
        }
    }

    public void clearVillage() {
        this.currentVillage = null;
    }

    public Optional<VillageAI> currentVillage() {
        return Optional.ofNullable(currentVillage);
    }
}
