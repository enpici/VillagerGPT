package io.github.enpici.villager.life.event;

import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class VillageStructureProgressEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final VillageAI village;
    private final String blueprintId;
    private final int placedSteps;
    private final int totalSteps;

    public VillageStructureProgressEvent(VillageAI village, String blueprintId, int placedSteps, int totalSteps) {
        this.village = village;
        this.blueprintId = blueprintId;
        this.placedSteps = placedSteps;
        this.totalSteps = totalSteps;
    }

    public VillageAI village() { return village; }
    public String blueprintId() { return blueprintId; }
    public int placedSteps() { return placedSteps; }
    public int totalSteps() { return totalSteps; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
