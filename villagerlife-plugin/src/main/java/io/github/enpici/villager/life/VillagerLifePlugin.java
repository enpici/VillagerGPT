package io.github.enpici.villager.life;

import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.ai.DecisionEngine;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.command.VillagerLifeCommand;
import io.github.enpici.villager.life.integration.VillagerLifeContextProvider;
import io.github.enpici.villager.life.scheduler.SimulationScheduler;
import io.github.enpici.villager.life.village.VillageManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import tj.horner.villagergpt.api.VillagerContextProvider;

public final class VillagerLifePlugin extends JavaPlugin {

    private AgentManager agentManager;
    private VillageManager villageManager;
    private BlueprintService blueprintService;
    private SimulationScheduler simulationScheduler;
    private VillagerLifeContextProvider contextProvider;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.agentManager = new AgentManager();
        this.villageManager = new VillageManager();
        this.blueprintService = new BlueprintService(this);
        blueprintService.loadFromDisk();

        var decisionEngine = new DecisionEngine();
        this.simulationScheduler = new SimulationScheduler(this, agentManager, villageManager, decisionEngine);
        this.contextProvider = new VillagerLifeContextProvider(agentManager, villageManager);

        getServer().getServicesManager().register(VillagerContextProvider.class, contextProvider, this, ServicePriority.High);

        var command = new VillagerLifeCommand(agentManager, villageManager, blueprintService);
        var pluginCommand = getCommand("villagerlife");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        simulationScheduler.start();
        getLogger().info("VillagerLife enabled. MVP skeleton activo.");
    }

    @Override
    public void onDisable() {
        if (simulationScheduler != null) {
            simulationScheduler.stop();
        }
        if (contextProvider != null) {
            getServer().getServicesManager().unregister(VillagerContextProvider.class, contextProvider);
        }
    }
}
