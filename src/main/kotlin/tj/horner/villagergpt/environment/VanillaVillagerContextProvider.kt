package tj.horner.villagergpt.environment

import org.bukkit.entity.Villager
import org.bukkit.persistence.PersistentDataType
import com.destroystokyo.paper.entity.villager.ReputationType
import tj.horner.villagergpt.PersistentDataKeys
import tj.horner.villagergpt.api.DefaultVillagerContext
import tj.horner.villagergpt.api.VillagerContext
import tj.horner.villagergpt.api.VillagerContextProvider
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class VanillaVillagerContextProvider(
    private val eventsByVillager: ConcurrentHashMap<UUID, ArrayDeque<String>>
) : VillagerContextProvider {
    override fun getContext(villager: Villager): VillagerContext {
        val nearbyVillagers = villager.getNearbyEntities(48.0, 24.0, 48.0)
            .count { it is Villager } + 1
        val personality = villager.persistentDataContainer.get(PersistentDataKeys.PERSONALITY, PersistentDataType.STRING)
        val recentEvents = eventsByVillager[villager.uniqueId]?.let { queue ->
            synchronized(queue) { queue.toList() }
        }.orEmpty()
        val relationship = villager.world.players.associate { player ->
            val rep = villager.getReputation(player.uniqueId)
            val score = ReputationType.values().sumOf { rep?.getReputation(it) ?: 0 }
            player.uniqueId to score
        }

        return DefaultVillagerContext(
            villagerUuid = villager.uniqueId,
            villagerName = villager.customName()?.toString() ?: villager.name,
            profession = villager.profession,
            personalityArchetype = personality,
            villagePopulation = nearbyVillagers,
            recentEvents = recentEvents,
            relationshipsWithPlayers = relationship
        )
    }
}
