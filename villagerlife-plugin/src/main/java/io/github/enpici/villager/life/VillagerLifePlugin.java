package io.github.enpici.villager.life;

import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.ai.AgentAiPlanner;
import io.github.enpici.villager.life.ai.AgentPlanner;
import io.github.enpici.villager.life.ai.DecisionEngine;
import io.github.enpici.villager.life.ai.ReproductionService;
import io.github.enpici.villager.life.ai.RoleAssignmentService;
import io.github.enpici.villager.life.blueprint.BuildTelemetry;
import io.github.enpici.villager.life.blueprint.BlueprintService;
import io.github.enpici.villager.life.command.VillagerLifeCommand;
import io.github.enpici.villager.life.integration.CitizensAdapter;
import io.github.enpici.villager.life.integration.CitizensGateway;
import io.github.enpici.villager.life.integration.VillagerLifeContextProvider;
import io.github.enpici.villager.life.listener.AgentLifecycleListener;
import io.github.enpici.villager.life.listener.ThreatListener;
import io.github.enpici.villager.life.listener.ThreatSignalListener;
import io.github.enpici.villager.life.observability.SimulationJournal;
import io.github.enpici.villager.life.persistence.LifeRepository;
import io.github.enpici.villager.life.planning.AiShelterPlanner;
import io.github.enpici.villager.life.scheduler.SimulationScheduler;
import io.github.enpici.villager.life.village.VillageManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.enpici.villager.api.VillagerContextProvider;
import io.github.enpici.villager.api.VillagerGPTService;

import java.io.File;

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
    private LifeRepository lifeRepository;
    private SimulationJournal simulationJournal;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.citizensAdapter = new CitizensAdapter();
        this.citizensIntegrationEnabled = resolveCitizensIntegrationEnabled();
        logCitizensMode();

        this.agentManager = new AgentManager(citizensIntegrationEnabled ? citizensAdapter : null);
        this.villageManager = new VillageManager();
        this.simulationJournal = new SimulationJournal(this);
        this.buildTelemetry = new BuildTelemetry(this);
        this.blueprintService = new BlueprintService(this, buildTelemetry);
        blueprintService.loadFromDisk();

        var agentPlanner = new AgentPlanner();
        var decisionEngine = new DecisionEngine();
        var reproductionService = new ReproductionService(this);
        var roleAssignmentService = new RoleAssignmentService();
        var aiShelterPlanner = new AiShelterPlanner(this);
        var agentAiPlanner = new AgentAiPlanner(this);
        this.lifeRepository = new LifeRepository(this, new File(getDataFolder(), getConfig().getString("persistence.db-path", "life.db")));
        lifeRepository.open();
        lifeRepository.load(villageManager, agentManager, blueprintService);

        this.simulationScheduler = new SimulationScheduler(
                this,
                agentManager,
                villageManager,
                agentPlanner,
                decisionEngine,
                reproductionService,
                roleAssignmentService,
                lifeRepository,
                simulationJournal,
                aiShelterPlanner,
                agentAiPlanner
        );
        this.contextProvider = new VillagerLifeContextProvider(agentManager, villageManager);

        getServer().getServicesManager().register(VillagerContextProvider.class, contextProvider, this, ServicePriority.High);
        VillagerGPTService gptService = getServer().getServicesManager().load(VillagerGPTService.class);
        if (gptService != null) {
            gptService.setContextProvider(contextProvider);
            getLogger().info("VillagerLife context provider connected to VillagerGPT.");
        } else {
            getLogger().warning("VillagerGPT service not found; dialogue will use vanilla context until connected.");
        }

        var command = new VillagerLifeCommand(agentManager, villageManager, blueprintService, simulationJournal, lifeRepository);
        var pluginCommand = getCommand("villagerlife");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(new ThreatListener(agentManager, villageManager, simulationJournal), this);
        getServer().getPluginManager().registerEvents(new AgentLifecycleListener(agentManager, villageManager, simulationJournal), this);

        getServer().getPluginManager().registerEvents(new ThreatSignalListener(villageManager, agentManager), this);

        simulationScheduler.start();
        getLogger().info("VillagerLife enabled. MVP skeleton activo.");
        simulationJournal.record("plugin_enabled", null, null, "VillagerLife ready");
    }

    @Override
    public void onDisable() {
        if (simulationScheduler != null) {
            simulationScheduler.stop();
        }
        if (lifeRepository != null) {
            lifeRepository.close();
        }
        VillagerGPTService gptService = getServer().getServicesManager().load(VillagerGPTService.class);
        if (gptService != null) {
            gptService.setContextProvider(null);
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

    public SimulationJournal simulationJournal() {
        return simulationJournal;
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
