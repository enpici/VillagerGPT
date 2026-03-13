package io.github.enpici.villager.life;

import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.ai.DecisionEngine;
import io.github.enpici.villager.life.blueprint.BuildTelemetry;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.command.VillagerLifeCommand;
import io.github.enpici.villager.life.integration.CitizensAdapter;
import io.github.enpici.villager.life.persistence.SimulationStatePersistence;
import io.github.enpici.villager.life.integration.CitizensGateway;
import io.github.enpici.villager.life.integration.VillagerLifeContextProvider;
import io.github.enpici.villager.life.scheduler.SimulationScheduler;
import io.github.enpici.villager.life.village.VillageManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.enpici.villager.api.VillagerContextProvider;

public final class VillagerLifePlugin extends JavaPlugin {

    private static VillagerLifePlugin instance;

    private AgentManager agentManager;
    private VillageManager villageManager;
    private BlueprintService blueprintService;
    private BuildTelemetry buildTelemetry;
    private SimulationScheduler simulationScheduler;
    private VillagerLifeContextProvider contextProvider;
    private CitizensGateway citizensAdapter;
    private boolean citizensIntegrationEnabled;
    private SimulationStatePersistence statePersistence;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.citizensAdapter = new CitizensAdapter();
        this.citizensIntegrationEnabled = resolveCitizensIntegrationEnabled();
        logCitizensMode();

        this.agentManager = new AgentManager(citizensIntegrationEnabled ? citizensAdapter : null);
        this.villageManager = new VillageManager();
        this.buildTelemetry = new BuildTelemetry(this);
        this.blueprintService = new BlueprintService(this, buildTelemetry);
        blueprintService.loadFromDisk();

        var decisionEngine = new DecisionEngine();
        this.simulationScheduler = new SimulationScheduler(this, agentManager, villageManager, decisionEngine);
        this.statePersistence = new SimulationStatePersistence(this, agentManager, villageManager, blueprintService, simulationScheduler);
        this.agentManager.setPersistenceListener(statePersistence);
        this.villageManager.setPersistenceListener(statePersistence);
        this.simulationScheduler.setQueueChangeListener(statePersistence::onBuildQueueChanged);
        this.contextProvider = new VillagerLifeContextProvider(agentManager, villageManager);

        getServer().getServicesManager().register(VillagerContextProvider.class, contextProvider, this, ServicePriority.High);

        var command = new VillagerLifeCommand(agentManager, villageManager, blueprintService);
        var pluginCommand = getCommand("villagerlife");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        statePersistence.load();
        statePersistence.startBatching();
        simulationScheduler.start();
        getLogger().info("VillagerLife enabled. MVP skeleton activo.");
    }

    @Override
    public void onDisable() {
        if (simulationScheduler != null) {
            simulationScheduler.stop();
        }
        if (statePersistence != null) {
            statePersistence.stopBatching();
            statePersistence.flushNow();
        }
        if (contextProvider != null) {
            getServer().getServicesManager().unregister(VillagerContextProvider.class, contextProvider);
        }
    }

    public static VillagerLifePlugin instance() {
        return instance;
    }

    public boolean isCitizensIntegrationEnabled() {
        return citizensIntegrationEnabled;
    }

    public CitizensGateway citizensAdapter() {
        return citizensAdapter;
    }

    private boolean resolveCitizensIntegrationEnabled() {
        boolean requested = getConfig().getBoolean("integration.citizens-enabled", true);
        if (!requested) {
            return false;
        }
        return citizensAdapter.isAvailable();
    }

    private void logCitizensMode() {
        boolean requested = getConfig().getBoolean("integration.citizens-enabled", true);
        if (!requested) {
            getLogger().info("Citizens integration INACTIVE: disabled by config (integration.citizens-enabled=false).");
            return;
        }
        if (citizensIntegrationEnabled) {
            getLogger().info("Citizens integration ACTIVE: plugin detected and API ready.");
            return;
        }
        getLogger().info("Citizens integration INACTIVE: plugin not installed, disabled, or API unavailable.");
    }
}
