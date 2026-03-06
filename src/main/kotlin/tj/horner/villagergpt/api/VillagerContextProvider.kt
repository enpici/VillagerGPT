package tj.horner.villagergpt.api

import org.bukkit.entity.Villager

fun interface VillagerContextProvider {
    fun getContext(villager: Villager): VillagerContext
}

