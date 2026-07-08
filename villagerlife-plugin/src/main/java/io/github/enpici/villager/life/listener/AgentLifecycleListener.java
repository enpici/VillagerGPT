package io.github.enpici.villager.life.listener;

import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.observability.SimulationJournal;
import io.github.enpici.villager.life.village.VillageManager;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class AgentLifecycleListener implements Listener {

    private final AgentManager agentManager;
    private final VillageManager villageManager;
    private final SimulationJournal journal;

    public AgentLifecycleListener(AgentManager agentManager, VillageManager villageManager, SimulationJournal journal) {
        this.agentManager = agentManager;
        this.villageManager = villageManager;
        this.journal = journal;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        agentManager.find(villager).ifPresent(agent -> {
            String killer = event.getEntity().getKiller() != null ? event.getEntity().getKiller().getName() : "unknown";
            String location = villager.getWorld().getName()
                    + ":" + villager.getLocation().getBlockX()
                    + "," + villager.getLocation().getBlockY()
                    + "," + villager.getLocation().getBlockZ();
            journal.record(
                    "agent_died",
                    villageManager.currentVillage().orElse(null),
                    agent,
                    "entity=" + villager.getType() + " killer=" + killer + " loc=" + location
            );
            agentManager.unregister(agent.villagerUuid());
        });
    }
}
