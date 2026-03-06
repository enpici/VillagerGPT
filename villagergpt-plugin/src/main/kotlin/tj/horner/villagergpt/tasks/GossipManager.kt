package tj.horner.villagergpt.tasks

import org.bukkit.entity.Villager
import org.bukkit.scheduler.BukkitRunnable
import tj.horner.villagergpt.VillagerGPT
import java.util.UUID

class GossipManager(private val plugin: VillagerGPT) : BukkitRunnable() {
    private val radius: Double = plugin.config.getDouble("gossip.radius", 10.0)
    private val radiusSquared = radius * radius

    override fun run() {
        val gossipLimit = plugin.config.getInt("gossip.max-entries", 30)

        plugin.server.worlds.forEach { world ->
            val villagers = world.getEntitiesByClass(Villager::class.java).toList()
            for (i in villagers.indices) {
                val villagerA = villagers[i]
                for (j in i + 1 until villagers.size) {
                    val villagerB = villagers[j]
                    if (villagerA.location.distanceSquared(villagerB.location) <= radiusSquared) {
                        shareGossip(villagerA.uniqueId, villagerB.uniqueId, gossipLimit)
                    }
                }
            }
        }
    }

    private fun shareGossip(idA: UUID, idB: UUID, limit: Int) {
        val memory = plugin.memory
        val gossipA = memory.loadGossip(idA, limit)
        val gossipB = memory.loadGossip(idB, limit)

        if (gossipA.isEmpty() && gossipB.isEmpty()) return

        val shareCount = 3
        val toB = gossipA.shuffled().take(shareCount)
        val toA = gossipB.shuffled().take(shareCount)

        memory.addGossip(idB, toB, limit)
        memory.addGossip(idA, toA, limit)
    }
}

