package io.github.enpici.villager.life.integration;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Villager;

public interface CitizensGateway {

    boolean isAvailable();

    NPC getOrCreateNpc(Villager villager);

    boolean navigateTo(Location location);

    Location currentLocation();

    void equipMainHand(Material material);

    boolean damageMainHand(Material expectedMaterial, int amount);

    void playSwingAnimation();
}

