package io.github.enpici.villager.life.persistence;

import java.util.Map;
import java.util.UUID;

public record AgentStateRecord(
        UUID villagerUuid,
        String role,
        Map<String, Double> needs,
        Integer npcId,
        String lastEvent,
        String activeTask
) {
}
