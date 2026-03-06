package io.github.enpici.villager.life.integration;

import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.village.VillageManager;
import tj.horner.villagergpt.api.DefaultVillagerContext;
import tj.horner.villagergpt.api.VillagerContext;
import tj.horner.villagergpt.api.VillagerContextProvider;
import org.bukkit.entity.Villager;

import java.util.List;

public class VillagerLifeContextProvider implements VillagerContextProvider {

    private final AgentManager agentManager;
    private final VillageManager villageManager;

    public VillagerLifeContextProvider(AgentManager agentManager, VillageManager villageManager) {
        this.agentManager = agentManager;
        this.villageManager = villageManager;
    }

    @Override
    public VillagerContext getContext(Villager villager) {
        var agent = agentManager.find(villager).orElse(null);
        var village = villageManager.currentVillage().orElse(null);

        return new DefaultVillagerContext(
                villager.getUniqueId(),
                villager.customName() != null ? villager.customName().toString() : "Villager",
                villager.getProfession(),
                null,
                agent != null ? agent.role().name() : null,
                agent != null ? agent.needLevel(NeedType.HUNGER) : null,
                agent != null ? agent.needLevel(NeedType.ENERGY) : null,
                village != null ? village.name() : null,
                village != null ? village.population() : null,
                village != null ? village.foodStock() : null,
                village != null ? village.pendingMaterials().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().name().toLowerCase(), java.util.Map.Entry::getValue))
                        : java.util.Map.of(),
                agent != null ? List.of(agent.lastEvent()) : List.of(),
                agent != null ? agent.relationshipsSnapshot() : java.util.Map.of()
        );
    }
}
