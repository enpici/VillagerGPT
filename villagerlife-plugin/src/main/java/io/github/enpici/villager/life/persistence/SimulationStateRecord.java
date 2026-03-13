package io.github.enpici.villager.life.persistence;

import java.util.List;

public record SimulationStateRecord(
        VillageStateRecord village,
        List<AgentStateRecord> agents,
        BuildQueueStateRecord buildQueues
) {
}
