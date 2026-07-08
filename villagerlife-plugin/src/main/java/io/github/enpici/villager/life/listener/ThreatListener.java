package io.github.enpici.villager.life.listener;

import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.observability.SimulationJournal;
import io.github.enpici.villager.life.village.VillageManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

public class ThreatListener implements Listener {

    private final AgentManager agentManager;
    private final VillageManager villageManager;
    private final SimulationJournal journal;

    public ThreatListener(AgentManager agentManager, VillageManager villageManager, SimulationJournal journal) {
        this.agentManager = agentManager;
        this.villageManager = villageManager;
        this.journal = journal;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Monster) || !(event.getTarget() instanceof Villager villager)) {
            return;
        }
        markThreat(villager, "threat:targeted_by:" + event.getEntityType().name().toLowerCase());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        Entity damager = event.getDamager();
        if (!(damager instanceof Monster)) {
            return;
        }
        markThreat(villager, "threat:damaged_by:" + damager.getType().name().toLowerCase());
    }

    private void markThreat(Villager villager, String event) {
        agentManager.find(villager).ifPresent(agent -> {
            villageManager.currentVillage().ifPresent(village -> village.markThreat());
            agent.adjustNeed(NeedType.SAFETY, 35);
            agent.setLastEvent(event);
            if (journal != null) {
                journal.record("threat_detected", villageManager.currentVillage().orElse(null), agent, event);
            }
        });
    }
}
