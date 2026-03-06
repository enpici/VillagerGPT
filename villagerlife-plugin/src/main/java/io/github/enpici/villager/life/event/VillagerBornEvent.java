package io.github.enpici.villager.life.event;

import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class VillagerBornEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final VillageAI village;
    private final Villager villager;

    public VillagerBornEvent(VillageAI village, Villager villager) {
        this.village = village;
        this.villager = villager;
    }

    public VillageAI village() { return village; }
    public Villager villager() { return villager; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
