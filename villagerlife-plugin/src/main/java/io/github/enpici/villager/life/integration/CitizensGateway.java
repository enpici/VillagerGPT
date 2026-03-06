package io.github.enpici.villager.life.integration;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Villager;

public interface CitizensGateway {

    boolean isAvailable();

    NPC getOrCreateNpc(Villager villager);

    boolean navigateTo(Location location);

    void playSwingAnimation();
}

