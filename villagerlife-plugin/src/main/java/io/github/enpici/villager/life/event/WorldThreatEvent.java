package io.github.enpici.villager.life.event;

import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class WorldThreatEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String source;
    private final Location location;

    public WorldThreatEvent(String source, Location location) {
        this.source = source;
        this.location = location;
    }

    public String source() {
        return source;
    }

    public Location location() {
        return location;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
