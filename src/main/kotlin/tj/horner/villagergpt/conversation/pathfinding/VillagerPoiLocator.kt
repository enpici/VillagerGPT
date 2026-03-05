package tj.horner.villagergpt.conversation.pathfinding

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.entity.Villager
import kotlin.math.abs

object VillagerPoiLocator {
    private val professionWorkstations = mapOf(
        Villager.Profession.FARMER to setOf(Material.COMPOSTER),
        Villager.Profession.FISHERMAN to setOf(Material.BARREL),
        Villager.Profession.SHEPHERD to setOf(Material.LOOM),
        Villager.Profession.FLETCHER to setOf(Material.FLETCHING_TABLE),
        Villager.Profession.LIBRARIAN to setOf(Material.LECTERN),
        Villager.Profession.CARTOGRAPHER to setOf(Material.CARTOGRAPHY_TABLE),
        Villager.Profession.CLERIC to setOf(Material.BREWING_STAND),
        Villager.Profession.ARMORER to setOf(Material.BLAST_FURNACE),
        Villager.Profession.WEAPONSMITH to setOf(Material.GRINDSTONE),
        Villager.Profession.TOOLSMITH to setOf(Material.SMITHING_TABLE),
        Villager.Profession.BUTCHER to setOf(Material.SMOKER),
        Villager.Profession.LEATHERWORKER to setOf(Material.CAULDRON),
        Villager.Profession.MASON to setOf(Material.STONECUTTER)
    )

    fun nearestBed(villager: Villager, searchRadius: Int): Location? {
        return nearestReachablePoi(villager, searchRadius) { block ->
            Tag.BEDS.isTagged(block.type)
        }
    }

    fun nearestWorkstation(villager: Villager, searchRadius: Int): Location? {
        val validWorkstations = professionWorkstations[villager.profession] ?: return null
        return nearestReachablePoi(villager, searchRadius) { block ->
            block.type in validWorkstations
        }
    }

    fun nearestMeetingPoint(villager: Villager, searchRadius: Int): Location? {
        return nearestReachablePoi(villager, searchRadius) { block -> block.type == Material.BELL }
    }

    internal fun workstationForProfession(profession: Villager.Profession): Set<Material> {
        return professionWorkstations[profession] ?: emptySet()
    }

    private fun nearestReachablePoi(
        villager: Villager,
        searchRadius: Int,
        matcher: (Block) -> Boolean
    ): Location? {
        val origin = villager.location
        val world = origin.world

        val minY = (origin.blockY - searchRadius).coerceAtLeast(world.minHeight)
        val maxY = (origin.blockY + searchRadius).coerceAtMost(world.maxHeight - 1)

        var nearest: Location? = null
        var nearestDistanceSquared = Double.MAX_VALUE

        for (x in origin.blockX - searchRadius..origin.blockX + searchRadius) {
            for (z in origin.blockZ - searchRadius..origin.blockZ + searchRadius) {
                val xDistance = abs(x - origin.blockX)
                val zDistance = abs(z - origin.blockZ)
                if (xDistance > searchRadius || zDistance > searchRadius) continue

                for (y in minY..maxY) {
                    val block = world.getBlockAt(x, y, z)
                    if (!matcher(block)) continue

                    val destination = block.location.clone().add(0.5, 1.0, 0.5)
                    val distanceSquared = origin.distanceSquared(destination)
                    if (distanceSquared >= nearestDistanceSquared) continue

                    val path = villager.pathfinder.findPath(destination)
                    if (path == null) continue

                    nearestDistanceSquared = distanceSquared
                    nearest = destination
                }
            }
        }

        return nearest
    }
}
