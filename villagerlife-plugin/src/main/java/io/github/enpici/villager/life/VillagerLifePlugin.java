package io.github.enpici.villager.life;

import org.bukkit.entity.Villager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import tj.horner.villagergpt.api.DefaultVillagerContext;
import tj.horner.villagergpt.api.VillagerContextProvider;

public final class VillagerLifePlugin extends JavaPlugin {

    private final VillagerContextProvider contextProvider = villager -> new DefaultVillagerContext(
            villager.getUniqueId(),
            villager.customName() != null ? villager.customName().toString() : "Villager",
            Villager.Profession.NONE,
            null,
            "idle",
            null,
            null,
            null,
            null,
            null,
            java.util.List.of(),
            java.util.Map.of()
    );

    @Override
    public void onEnable() {
        getServer().getServicesManager().register(VillagerContextProvider.class, contextProvider, this, ServicePriority.Normal);
        getLogger().info("VillagerLife enabled: VillagerContextProvider registrado.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregister(VillagerContextProvider.class, contextProvider);
    }
}
