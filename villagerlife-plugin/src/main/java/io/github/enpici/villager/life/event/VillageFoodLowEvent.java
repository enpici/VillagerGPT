package io.github.enpici.villager.life.event;

import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class VillageFoodLowEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final VillageAI village;
    private final int foodStock;

    public VillageFoodLowEvent(VillageAI village, int foodStock) {
        this.village = village;
        this.foodStock = foodStock;
    }

    public VillageAI village() { return village; }
    public int foodStock() { return foodStock; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
