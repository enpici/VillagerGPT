package io.github.enpici.villager.life.village;

import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import org.bukkit.Location;

import java.util.Optional;
import java.util.UUID;

public class VillageManager {

    private VillageAI currentVillage;

    public VillageAI createVillage(String name, Location center, AgentManager agentManager, BlueprintService blueprintService) {
        this.currentVillage = new VillageAI(UUID.randomUUID(), name, center, agentManager, blueprintService);
        return currentVillage;
    }

    public Optional<VillageAI> currentVillage() {
        return Optional.ofNullable(currentVillage);
    }
}
