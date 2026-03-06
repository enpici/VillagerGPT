package io.github.enpici.villager.life.event;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.role.AgentRole;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AgentRoleChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Agent agent;
    private final AgentRole previousRole;
    private final AgentRole newRole;

    public AgentRoleChangedEvent(Agent agent, AgentRole previousRole, AgentRole newRole) {
        this.agent = agent;
        this.previousRole = previousRole;
        this.newRole = newRole;
    }

    public Agent agent() { return agent; }
    public AgentRole previousRole() { return previousRole; }
    public AgentRole newRole() { return newRole; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
