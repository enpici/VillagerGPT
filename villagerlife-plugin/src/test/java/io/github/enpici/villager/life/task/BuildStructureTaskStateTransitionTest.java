package io.github.enpici.villager.life.task;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import io.github.enpici.villager.life.VillagerLifePlugin;
import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.blueprint.BuildTelemetry;
import io.github.enpici.villager.life.build.BlockPlacementStep;
import io.github.enpici.villager.life.event.VillageStructureBuiltEvent;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.task.impl.BuildStructureTask;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("MockBukkit v1.21 tag parser is incompatible with current Paper API in this environment")
class BuildStructureTaskStateTransitionTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void transitionsAndEmitsCompletionEventWithCitizensFallback() {
        server = MockBukkit.mock();
        VillagerLifePlugin plugin = MockBukkit.load(VillagerLifePlugin.class);
        World world = server.addSimpleWorld("world");

        Villager villager = world.spawn(new Location(world, 0, 64, 0), Villager.class);
        Agent agent = new Agent(villager.getUniqueId(), AgentRole.BUILDER);

        Location target = new Location(world, 8, 64, 0);
        BlockPlacementStep step = new BlockPlacementStep(Material.STONE, target, List.of());

        FakeBlueprintService blueprintService = new FakeBlueprintService(plugin, step, 0);
        VillageAI village = new VillageAI(UUID.randomUUID(), "test", new Location(world, 0, 64, 0), new AgentManager(null), blueprintService);

        CompletionListener listener = new CompletionListener();
        server.getPluginManager().registerEvents(listener, plugin);

        BuildStructureTask task = new BuildStructureTask("house_small");
        task.start(agent, village);

        for (int i = 0; i < 8 && task.status() == TaskStatus.RUNNING; i++) {
            task.tick(agent, village);
            server.getScheduler().performOneTick();
        }

        assertEquals(TaskStatus.SUCCESS, task.status());
        assertEquals(Material.STONE, target.getBlock().getType());
        assertTrue(villager.getLocation().distanceSquared(target.clone().add(0.5, 0, 0.5)) <= 4.0, "fallback sin Citizens debería teletransportar al aldeano");
        assertEquals(1, listener.events.get());
    }

    @Test
    void retriesPlacementThenTimesOutWhenBlockCannotBePlaced() {
        server = MockBukkit.mock();
        VillagerLifePlugin plugin = MockBukkit.load(VillagerLifePlugin.class);
        World world = server.addSimpleWorld("world");

        Villager villager = world.spawn(new Location(world, 0, 64, 0), Villager.class);
        Agent agent = new Agent(villager.getUniqueId(), AgentRole.BUILDER);

        BlockPlacementStep step = new BlockPlacementStep(Material.STONE, new Location(world, 1, 64, 0), List.of());
        FakeBlueprintService blueprintService = new FakeBlueprintService(plugin, step, Integer.MAX_VALUE);
        VillageAI village = new VillageAI(UUID.randomUUID(), "timeout", new Location(world, 0, 64, 0), new AgentManager(null), blueprintService);

        BuildStructureTask task = new BuildStructureTask("house_small");
        task.start(agent, village);

        for (int i = 0; i < 1300 && task.status() == TaskStatus.RUNNING; i++) {
            task.tick(agent, village);
            server.getScheduler().performOneTick();
        }

        assertEquals(TaskStatus.TIMEOUT, task.status());
        assertTrue(blueprintService.placeAttempts.get() >= 2, "debe reintentar antes de agotarse");
    }

    private static class FakeBlueprintService extends BlueprintService {
        private final io.github.enpici.villager.life.build.BuildPlan plan;
        private final int failsBeforeSuccess;
        private final AtomicInteger placeAttempts = new AtomicInteger();

        private FakeBlueprintService(VillagerLifePlugin plugin, BlockPlacementStep step, int failsBeforeSuccess) {
            super(plugin, new BuildTelemetry(plugin));
            this.plan = new io.github.enpici.villager.life.build.BuildPlan(List.of(step));
            this.failsBeforeSuccess = failsBeforeSuccess;
        }

        @Override
        public boolean hasBlueprint(String id) {
            return true;
        }

        @Override
        public Optional<io.github.enpici.villager.life.build.BuildPlan> extractBuildPlan(String id, Location origin) {
            return Optional.of(plan);
        }

        @Override
        public boolean placeBlockAt(BlockPlacementStep step) {
            int attempt = placeAttempts.incrementAndGet();
            if (attempt <= failsBeforeSuccess) {
                return false;
            }
            step.position().getBlock().setType(step.material());
            return true;
        }
    }

    private static class CompletionListener implements Listener {
        private final AtomicInteger events = new AtomicInteger();

        @EventHandler
        public void onBuilt(VillageStructureBuiltEvent ignored) {
            events.incrementAndGet();
        }
    }
}
