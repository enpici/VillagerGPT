package io.github.enpici.villager.life.integration;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CitizensAdapter {

    private final Map<UUID, Integer> villagerNpcMap = new ConcurrentHashMap<>();
    private NPC currentNpc;

    public boolean isAvailable() {
        Plugin citizens = Bukkit.getPluginManager().getPlugin("Citizens");
        return citizens != null && citizens.isEnabled() && CitizensAPI.hasImplementation();
    }

    public NPC getOrCreateNpc(Villager villager) {
        if (!isAvailable() || villager == null || !villager.isValid()) {
            currentNpc = null;
            return null;
        }

        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        Integer existingId = villagerNpcMap.get(villager.getUniqueId());
        if (existingId != null) {
            NPC npc = registry.getById(existingId);
            if (npc != null) {
                currentNpc = npc;
                return npc;
            }
        }

        NPC npc = registry.createNPC(EntityType.VILLAGER, villager.getName());
        npc.spawn(villager.getLocation());
        villagerNpcMap.put(villager.getUniqueId(), npc.getId());
        currentNpc = npc;
        return npc;
    }

    public boolean navigateTo(Location location) {
        if (currentNpc == null || !currentNpc.isSpawned() || location == null) {
            return false;
        }

        currentNpc.getNavigator().setTarget(location);
        return true;
    }

    public void playSwingAnimation() {
        if (currentNpc == null || !currentNpc.isSpawned()) {
            return;
        }

        if (currentNpc.getEntity() instanceof Villager villager) {
            villager.swingMainHand();
        }
    }
}
