package io.github.enpici.villager.life.scheduler;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.ai.DecisionEngine;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;
import io.github.enpici.villager.life.village.VillageManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class SimulationScheduler {

    private final JavaPlugin plugin;
    private final AgentManager agentManager;
    private final VillageManager villageManager;
    private final DecisionEngine decisionEngine;

    private BukkitTask taskTick;
    private BukkitTask decisionTick;
    private BukkitTask villageTick;

    public SimulationScheduler(JavaPlugin plugin, AgentManager agentManager, VillageManager villageManager, DecisionEngine decisionEngine) {
        this.plugin = plugin;
        this.agentManager = agentManager;
        this.villageManager = villageManager;
        this.decisionEngine = decisionEngine;
    }

    public void start() {
        taskTick = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickActiveTasks, 1L, 1L);
        decisionTick = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickDecisions, 20L, 20L);
        villageTick = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickVillagePlanning, 200L, 200L);
    }

    public void stop() {
        if (taskTick != null) taskTick.cancel();
        if (decisionTick != null) decisionTick.cancel();
        if (villageTick != null) villageTick.cancel();
    }

    private void tickActiveTasks() {
        VillageAI village = villageManager.currentVillage().orElse(null);
        if (village == null) return;

        for (Agent agent : agentManager.all()) {
            if (agent.activeTask() == null) continue;
            var status = agent.activeTask().tick(agent, village);
            if (status != TaskStatus.RUNNING && status != TaskStatus.PENDING) {
                agent.activeTask().stop(agent, village, status);
                agent.clearTask(status);
            }
        }
    }

    private void tickDecisions() {
        VillageAI village = villageManager.currentVillage().orElse(null);
        if (village == null) return;

        for (Agent agent : agentManager.all()) {
            agent.decayNeeds();
            if (agent.activeTask() != null) continue;
            var newTask = decisionEngine.decide(agent, village);
            if (newTask.canStart(agent, village)) {
                newTask.start(agent, village);
                agent.assignTask(newTask);
                agent.setLastEvent("task:" + newTask.id());
            }
        }
    }

    private void tickVillagePlanning() {
        VillageAI village = villageManager.currentVillage().orElse(null);
        if (village == null) return;
        village.planVillage();
        village.ensureBasicNeedsForGrowth();
        village.tryReproduce();
    }
}
