package io.github.enpici.villager.life.blueprint;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.integration.WorldEditGateway;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("MockBukkit v1.21 tag parser is incompatible with current Paper API in this environment")
class BlueprintWorldEditFallbackRegressionTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void retriesAndRecordsFailureWhenWorldEditIsUnavailable() throws Exception {
        server = MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin();

        File blueprintsDir = new File(plugin.getDataFolder(), "blueprints");
        assertTrue(blueprintsDir.mkdirs() || blueprintsDir.exists());
        assertTrue(new File(blueprintsDir, "house_small.schem").createNewFile());

        BuildTelemetry telemetry = new BuildTelemetry(plugin);
        BlueprintService service = new BlueprintService(plugin, telemetry, new BrokenWorldEditGateway());
        service.loadFromDisk();

        World world = server.addSimpleWorld("world");
        VillageAI village = new VillageAI(UUID.randomUUID(), "v", new Location(world, 0, 64, 0), new AgentManager(null), service);

        boolean result = service.placeStructure("house_small", village, null, new Location(world, 0, 64, 0));

        assertFalse(result);
        BuildTelemetry.BuildCounters counters = telemetry.snapshotCounters();
        assertEquals(1, counters.failed());
        assertEquals(2, counters.retries());
        assertEquals("schematic_io_error", telemetry.recentFailuresSnapshot(1).getFirst().reason());
    }

    private static class BrokenWorldEditGateway implements WorldEditGateway {
        @Override
        public Clipboard readClipboard(File schemFile) throws IOException {
            throw new IOException("WorldEdit API unavailable");
        }

        @Override
        public boolean pasteClipboard(Clipboard clipboard, Location destination, int maxSchematicBlocks, String blueprintId) {
            return false;
        }
    }
}
