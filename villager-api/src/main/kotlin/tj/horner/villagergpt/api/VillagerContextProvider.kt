package io.github.enpici.villager.api

import org.bukkit.entity.Villager

fun interface VillagerContextProvider {
    fun getContext(villager: Villager): VillagerContext
}

