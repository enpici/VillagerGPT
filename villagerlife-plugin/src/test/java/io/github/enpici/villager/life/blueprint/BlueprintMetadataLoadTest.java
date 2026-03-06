package io.github.enpici.villager.life.blueprint;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("MockBukkit v1.21 tag parser is incompatible with current Paper API in this environment")
class BlueprintMetadataLoadTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void loadsBlueprintMetadataAndFallsBackToHeuristics() throws Exception {
        server = MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin();

        File blueprintsDir = new File(plugin.getDataFolder(), "blueprints");
        assertTrue(blueprintsDir.mkdirs() || blueprintsDir.exists());

        File house = new File(blueprintsDir, "house_small.schem");
        File tower = new File(blueprintsDir, "guard_tower.schem");
        assertTrue(house.createNewFile());
        assertTrue(tower.createNewFile());

        Files.writeString(new File(blueprintsDir, "house_small.yml").toPath(), """
                type: HOUSE
                capacity: 4
                tags:
                  - critical
                  - starter
                """);

        BlueprintService service = new BlueprintService(plugin, new BuildTelemetry(plugin));
        service.loadFromDisk();

        BlueprintDefinition fromMetadata = service.find("house_small").orElseThrow();
        assertEquals(BuildingType.HOUSE, fromMetadata.type());
        assertEquals(4, fromMetadata.capacity());
        assertEquals(Set.of("critical", "starter"), fromMetadata.tags());

        BlueprintDefinition fromHeuristics = service.find("guard_tower").orElseThrow();
        assertEquals(BuildingType.DEFENSIVE, fromHeuristics.type());
        assertTrue(fromHeuristics.tags().contains("guard"));
        assertTrue(fromHeuristics.tags().contains("tower"));
    }
}
