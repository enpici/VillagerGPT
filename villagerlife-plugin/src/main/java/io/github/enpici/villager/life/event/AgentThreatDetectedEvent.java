package io.github.enpici.villager.life.event;

import io.github.enpici.villager.life.agent.Agent;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AgentThreatDetectedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Agent agent;
    private final String source;

    public AgentThreatDetectedEvent(Agent agent, String source) {
        this.agent = agent;
        this.source = source;
    }

    public Agent agent() { return agent; }
    public String source() { return source; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
