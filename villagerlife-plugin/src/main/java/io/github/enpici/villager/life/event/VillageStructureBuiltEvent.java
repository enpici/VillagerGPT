package io.github.enpici.villager.life.event;

import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class VillageStructureBuiltEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final VillageAI village;
    private final String blueprintId;
    private final long totalTicks;
    private final int placedBlocks;
    private final int failedBlocks;

    public VillageStructureBuiltEvent(VillageAI village, String blueprintId, long totalTicks, int placedBlocks, int failedBlocks) {
        this.village = village;
        this.blueprintId = blueprintId;
        this.totalTicks = totalTicks;
        this.placedBlocks = placedBlocks;
        this.failedBlocks = failedBlocks;
    }

    public VillageAI village() { return village; }
    public String blueprintId() { return blueprintId; }
    public long totalTicks() { return totalTicks; }
    public int placedBlocks() { return placedBlocks; }
    public int failedBlocks() { return failedBlocks; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
