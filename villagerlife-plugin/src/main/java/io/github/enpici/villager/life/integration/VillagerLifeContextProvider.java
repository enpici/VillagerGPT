package io.github.enpici.villager.life.integration;

import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.village.VillageManager;
import io.github.enpici.villager.api.DefaultVillagerContext;
import io.github.enpici.villager.api.VillagerContext;
import io.github.enpici.villager.api.VillagerContextProvider;
import org.bukkit.entity.Villager;

import java.util.List;
import java.util.stream.Stream;

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
                agent != null ? agent.relationshipsSnapshot() : java.util.Map.of(),
                agent != null ? agent.lifeStage().name() : null,
                agent != null ? agent.generation() : null,
                agent != null ? agent.partner() : null,
                agent != null ? Stream.of(agent.parentA(), agent.parentB()).filter(java.util.Objects::nonNull).toList() : List.of(),
                agent != null && agent.activeTask() != null ? agent.activeTask().id() : null,
                agent != null ? agent.lastDecisionReason() : null,
                agent != null ? agent.currentGoal().name() : null,
                agent != null ? agent.currentGoalReason() : null,
                agent != null ? agent.currentGoalPriority() : null
        );
    }
}
