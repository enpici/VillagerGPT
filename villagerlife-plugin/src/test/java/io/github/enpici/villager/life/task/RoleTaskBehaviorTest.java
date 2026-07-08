package io.github.enpici.villager.life.task;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.agent.LifeStage;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.task.impl.CareForChildTask;
import io.github.enpici.villager.life.task.impl.CraftSuppliesTask;
import io.github.enpici.villager.life.task.impl.MineResourcesTask;
import io.github.enpici.villager.life.task.impl.TradeTask;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleTaskBehaviorTest {

    @Test
    void minerAddsStoneAndOreLikeAResourceRun() {
        Agent miner = new Agent(UUID.randomUUID(), AgentRole.MINER);
        VillageAI village = villageWith(miner);
        village.addMaterialStock(Material.WOODEN_PICKAXE, 1);

        TaskStatus status = run(new MineResourcesTask(), miner, village);

        assertEquals(TaskStatus.SUCCESS, status);
        assertTrue(village.availableMaterial(Material.COBBLESTONE) >= 5);
    }

    @Test
    void crafterTurnsLogsIntoPlanks() {
        Agent crafter = new Agent(UUID.randomUUID(), AgentRole.CRAFTER);
        VillageAI village = villageWith(crafter);
        village.addMaterialStock(Material.OAK_LOG, 1);

        TaskStatus status = run(new CraftSuppliesTask(), crafter, village);

        assertEquals(TaskStatus.SUCCESS, status);
        assertEquals(0, village.availableMaterial(Material.OAK_LOG));
        assertEquals(4, village.availableMaterial(Material.OAK_PLANKS));
    }

    @Test
    void stoneRequiresPickaxeCobblestoneDropAndSmelting() {
        Agent miner = new Agent(UUID.randomUUID(), AgentRole.MINER);
        Agent crafter = new Agent(UUID.randomUUID(), AgentRole.CRAFTER);
        AgentManager manager = new AgentManager(null);
        manager.restore(miner);
        manager.restore(crafter);
        VillageAI village = new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), manager, null);
        village.addMaterialStock(Material.WOODEN_PICKAXE, 1);
        village.addMaterialStock(Material.FURNACE, 1);
        village.addMaterialStock(Material.COAL, 1);
        village.enqueueMaterialRequests(java.util.Map.of(Material.STONE, 1));

        TaskStatus mineStatus = run(new MineResourcesTask(), miner, village);
        TaskStatus craftStatus = run(new CraftSuppliesTask(), crafter, village);

        assertEquals(TaskStatus.SUCCESS, mineStatus);
        assertEquals(TaskStatus.SUCCESS, craftStatus);
        assertEquals(1, village.availableMaterial(Material.STONE));
    }

    @Test
    void minerStartsByGatheringWoodByHandWhenToolIsMissing() {
        Agent miner = new Agent(UUID.randomUUID(), AgentRole.MINER);
        VillageAI village = villageWith(miner);
        village.enqueueMaterialRequests(java.util.Map.of(Material.STONE, 1));

        TaskStatus status = run(new MineResourcesTask(), miner, village);

        assertEquals(TaskStatus.SUCCESS, status);
        assertTrue(village.availableMaterial(Material.OAK_LOG) >= 1);
        assertEquals("role:mined:oak_log", miner.lastEvent());
    }

    @Test
    void minerUsesAxeAsPreferredToolForLogs() {
        Agent miner = new Agent(UUID.randomUUID(), AgentRole.MINER);
        VillageAI village = villageWith(miner);
        village.addMaterialStock(Material.WOODEN_AXE, 1);

        TaskStatus status = run(new MineResourcesTask(), miner, village);

        assertEquals(TaskStatus.SUCCESS, status);
        assertTrue(village.availableMaterial(Material.OAK_LOG) >= 5);
        assertEquals("role:mined:oak_log", miner.lastEvent());
    }

    @Test
    void minerSkipsCraftingRequestsAndHandlesNextGatherableMaterial() {
        Agent miner = new Agent(UUID.randomUUID(), AgentRole.MINER);
        VillageAI village = villageWith(miner);
        village.enqueueMaterialRequests(java.util.Map.of(Material.TORCH, 4));
        village.enqueueMaterialRequests(java.util.Map.of(Material.OAK_LOG, 1));

        TaskStatus status = run(new MineResourcesTask(), miner, village);

        assertEquals(TaskStatus.SUCCESS, status);
        assertTrue(village.availableMaterial(Material.OAK_LOG) >= 1);
        assertEquals(Material.TORCH, village.pollMaterialRequest().material());
    }

    @Test
    void minerTreatsAvailableRequestedMaterialAsSatisfied() {
        Agent miner = new Agent(UUID.randomUUID(), AgentRole.MINER);
        VillageAI village = villageWith(miner);
        village.addMaterialStock(Material.OAK_LOG, 8);
        village.enqueueMaterialRequests(java.util.Map.of(Material.OAK_LOG, 1));

        TaskStatus status = run(new MineResourcesTask(), miner, village);

        assertEquals(TaskStatus.SUCCESS, status);
        assertEquals(8, village.availableMaterial(Material.OAK_LOG));
        assertEquals("role:material_available:oak_log", miner.lastEvent());
    }

    @Test
    void crafterTreatsAvailableRawMaterialRequestAsSatisfied() {
        Agent crafter = new Agent(UUID.randomUUID(), AgentRole.CRAFTER);
        VillageAI village = villageWith(crafter);
        village.addMaterialStock(Material.OAK_LOG, 8);
        village.enqueueMaterialRequests(java.util.Map.of(Material.OAK_LOG, 1));

        TaskStatus status = run(new CraftSuppliesTask(), crafter, village);

        assertEquals(TaskStatus.SUCCESS, status);
        assertEquals(8, village.availableMaterial(Material.OAK_LOG));
        assertEquals("role:material_available:oak_log", crafter.lastEvent());
    }

    @Test
    void minerCraftsBasicPickaxeWhenInputsAreAvailable() {
        Agent miner = new Agent(UUID.randomUUID(), AgentRole.MINER);
        VillageAI village = villageWith(miner);
        village.addMaterialStock(Material.OAK_PLANKS, 3);
        village.addMaterialStock(Material.STICK, 2);
        village.enqueueMaterialRequests(java.util.Map.of(Material.STONE, 1));

        TaskStatus status = run(new MineResourcesTask(), miner, village);

        assertEquals(TaskStatus.SUCCESS, status);
        assertTrue(village.availableMaterial(Material.COBBLESTONE) >= 1);
    }

    @Test
    void traderCanBringFoodWhenStockIsLow() {
        Agent trader = new Agent(UUID.randomUUID(), AgentRole.TRADER);
        VillageAI village = villageWith(trader);
        village.setFoodStock(0);

        TaskStatus status = run(new TradeTask(), trader, village);

        assertEquals(TaskStatus.SUCCESS, status);
        assertTrue(village.foodStock() >= 4);
        assertTrue(village.availableMaterial(Material.EMERALD) >= 1);
    }

    @Test
    void parentCareReducesChildNeeds() {
        Agent parent = new Agent(UUID.randomUUID(), AgentRole.FARMER);
        Agent child = new Agent(UUID.randomUUID(), AgentRole.FARMER);
        child.setLifeStage(LifeStage.CHILD);
        child.setParents(parent.villagerUuid(), null);
        child.setNeed(NeedType.HUNGER, 80);
        child.setNeed(NeedType.SOCIAL, 80);
        AgentManager manager = new AgentManager(null);
        manager.restore(parent);
        manager.restore(child);
        VillageAI village = new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), manager, null);
        village.setFoodStock(2);

        TaskStatus status = run(new CareForChildTask(), parent, village);

        assertEquals(TaskStatus.SUCCESS, status);
        assertTrue(child.needLevel(NeedType.HUNGER) < 80);
        assertTrue(child.needLevel(NeedType.SOCIAL) < 80);
    }

    private VillageAI villageWith(Agent agent) {
        AgentManager manager = new AgentManager(null);
        manager.restore(agent);
        return new VillageAI(UUID.randomUUID(), "test", new Location(null, 0, 64, 0), manager, null);
    }

    private TaskStatus run(Task task, Agent agent, VillageAI village) {
        task.start(agent, village);
        return task.tick(agent, village);
    }
}
