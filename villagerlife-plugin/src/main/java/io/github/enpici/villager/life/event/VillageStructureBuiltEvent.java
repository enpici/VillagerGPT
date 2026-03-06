package io.github.enpici.villager.life.event;

import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class VillageStructureBuiltEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final VillageAI village;
    private final String blueprintId;

    public VillageStructureBuiltEvent(VillageAI village, String blueprintId) {
        this.village = village;
        this.blueprintId = blueprintId;
    }

    public VillageAI village() { return village; }
    public String blueprintId() { return blueprintId; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
