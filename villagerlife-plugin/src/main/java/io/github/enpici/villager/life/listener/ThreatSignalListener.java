package io.github.enpici.villager.life.listener;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentManager;
import io.github.enpici.villager.life.event.AgentThreatDetectedEvent;
import io.github.enpici.villager.life.event.WorldThreatEvent;
import io.github.enpici.villager.life.village.VillageManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ThreatSignalListener implements Listener {

    private static final long THREAT_SIGNAL_TTL = 300L;
    private final VillageManager villageManager;
    private final AgentManager agentManager;

    public ThreatSignalListener(VillageManager villageManager, AgentManager agentManager) {
        this.villageManager = villageManager;
        this.agentManager = agentManager;
    }

    @EventHandler
    public void onWorldThreat(WorldThreatEvent event) {
        villageManager.currentVillage().ifPresent(village -> {
            String source = "event:" + event.source();
            village.registerThreatSignal(source, event.location(), THREAT_SIGNAL_TTL);

            Agent guard = agentManager.all().stream()
                    .filter(agent -> agent.role() == io.github.enpici.villager.life.role.AgentRole.GUARD)
                    .findFirst()
                    .orElse(null);
            if (guard != null) {
                guard.setLastEvent("defense:threat:" + event.source());
                Bukkit.getPluginManager().callEvent(new AgentThreatDetectedEvent(guard, event.source()));
            }
        });
    }
}
