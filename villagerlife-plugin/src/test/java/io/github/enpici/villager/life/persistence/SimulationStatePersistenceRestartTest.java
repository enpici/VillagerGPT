package io.github.enpici.villager.life.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimulationStatePersistenceRestartTest {

    @Test
    void restoresVillageAgentsAndQueuesAfterSimulatedRestart() throws Exception {
        Path baseDir = Files.createTempDirectory("villagerlife-persistence-test");
        ObjectMapper mapper = new ObjectMapper();

        VillageStateRepository villageRepository = new VillageStateRepository(baseDir, mapper);
        AgentStateRepository agentRepository = new AgentStateRepository(baseDir, mapper);
        BuildQueueRepository queueRepository = new BuildQueueRepository(baseDir, mapper);

        UUID villageId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        villageRepository.save(new VillageStateRecord(
                villageId,
                "aldea-test",
                "world",
                20,
                65,
                -5,
                0,
                0,
                57,
                2,
                6,
                30,
                100,
                120,
                Map.of("OAK_LOG", 20),
                Map.of("OAK_LOG", 5),
                Map.of("COBBLESTONE", 16),
                List.of("house_small"),
                List.of("warehouse_large"),
                List.of(new VillageStateRecord.MaterialRequestRecord("OAK_LOG", 12))
        ));

        agentRepository.save(List.of(new AgentStateRecord(
                agentId,
                "BUILDER",
                Map.of("HUNGER", 33.5, "ENERGY", 41.0),
                42,
                "testing",
                "build_structure"
        )));

        queueRepository.save(new BuildQueueStateRecord(
                List.of("house_small"),
                List.of("warehouse_large"),
                List.of(new BuildQueueStateRecord.MaterialQueueRecord("COBBLESTONE", 32))
        ));

        VillageStateRepository villageRepositoryReloaded = new VillageStateRepository(baseDir, mapper);
        AgentStateRepository agentRepositoryReloaded = new AgentStateRepository(baseDir, mapper);
        BuildQueueRepository queueRepositoryReloaded = new BuildQueueRepository(baseDir, mapper);

        VillageStateRecord loadedVillage = villageRepositoryReloaded.load().orElseThrow();
        assertEquals(villageId, loadedVillage.id());
        assertEquals("aldea-test", loadedVillage.name());
        assertEquals(57, loadedVillage.foodStock());
        assertEquals(List.of("house_small"), loadedVillage.pendingQuickBlueprints());

        AgentStateRecord loadedAgent = agentRepositoryReloaded.load().get(0);
        assertEquals(agentId, loadedAgent.villagerUuid());
        assertEquals("BUILDER", loadedAgent.role());
        assertEquals("build_structure", loadedAgent.activeTask());

        BuildQueueStateRecord loadedQueue = queueRepositoryReloaded.load().orElseThrow();
        assertEquals(List.of("house_small"), loadedQueue.quickBlueprints());
        assertEquals(List.of("warehouse_large"), loadedQueue.longBlueprints());
        assertEquals(1, loadedQueue.pendingMaterials().size());
    }
}
