package io.github.enpici.villager.life.integration;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import io.github.enpici.villager.life.VillagerLifePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CitizensAdapter implements CitizensGateway {

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

        if (!autoSpawnNpcs()) {
            currentNpc = null;
            return null;
        }

        NPC npc = registry.createNPC(resolveBodyType(), resolveName(villager));
        npc.spawn(villager.getLocation());
        villagerNpcMap.put(villager.getUniqueId(), npc.getId());
        if (npc.getEntity() instanceof LivingEntity living) {
            living.setCustomName(resolveName(villager));
            living.setCustomNameVisible(true);
        }
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

    public Location currentLocation() {
        if (currentNpc == null || !currentNpc.isSpawned() || currentNpc.getEntity() == null) {
            return null;
        }
        return currentNpc.getEntity().getLocation();
    }

    public void equipMainHand(Material material) {
        if (material == null || currentNpc == null || !currentNpc.isSpawned()) {
            return;
        }
        if (currentNpc.getEntity() instanceof LivingEntity living && living.getEquipment() != null) {
            ItemStack current = living.getEquipment().getItemInMainHand();
            if (current.getType() != material) {
                living.getEquipment().setItemInMainHand(new ItemStack(material));
            }
        }
    }

    public boolean damageMainHand(Material expectedMaterial, int amount) {
        if (expectedMaterial == null || amount <= 0 || currentNpc == null || !currentNpc.isSpawned()) {
            return false;
        }
        if (!(currentNpc.getEntity() instanceof LivingEntity living) || living.getEquipment() == null) {
            return false;
        }
        ItemStack item = living.getEquipment().getItemInMainHand();
        if (item == null || item.getType() != expectedMaterial || item.getType().getMaxDurability() <= 0) {
            return false;
        }
        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            return false;
        }
        int nextDamage = damageable.getDamage() + amount;
        if (nextDamage >= item.getType().getMaxDurability()) {
            living.getEquipment().setItemInMainHand(null);
            return true;
        }
        damageable.setDamage(nextDamage);
        item.setItemMeta(damageable);
        living.getEquipment().setItemInMainHand(item);
        return false;
    }

    public void playSwingAnimation() {
        if (currentNpc == null || !currentNpc.isSpawned()) {
            return;
        }

        if (currentNpc.getEntity() instanceof LivingEntity living) {
            living.swingMainHand();
        }
    }

    private EntityType resolveBodyType() {
        VillagerLifePlugin plugin = VillagerLifePlugin.instance();
        String configured = plugin != null
                ? plugin.getConfig().getString("integration.citizens-body-type", "PLAYER")
                : "PLAYER";
        if ("VILLAGER".equalsIgnoreCase(configured)) {
            return EntityType.VILLAGER;
        }
        return EntityType.PLAYER;
    }

    private boolean autoSpawnNpcs() {
        VillagerLifePlugin plugin = VillagerLifePlugin.instance();
        return plugin == null || plugin.getConfig().getBoolean("integration.citizens-auto-spawn-npcs", true);
    }

    private String resolveName(Villager villager) {
        String base = villager.customName() != null ? villager.customName().toString() : villager.getName();
        return base == null || base.isBlank() ? "Village Agent" : base;
    }
}
