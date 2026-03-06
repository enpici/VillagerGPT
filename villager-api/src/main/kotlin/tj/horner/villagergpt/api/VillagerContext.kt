package tj.horner.villagergpt.api

import org.bukkit.entity.Villager
import java.util.UUID

interface VillagerContext {
    val villagerUuid: UUID
    val villagerName: String
    val profession: Villager.Profession
    val personalityArchetype: String?
    val currentRole: String?
    val hungerLevel: Double?
    val energyLevel: Double?
    val villageName: String?
    val villagePopulation: Int?
    val villageFoodStock: Int?
    val villagePendingMaterials: Map<String, Int>
    val recentEvents: List<String>
    val relationshipsWithPlayers: Map<UUID, Int>
}

data class DefaultVillagerContext(
    override val villagerUuid: UUID,
    override val villagerName: String,
    override val profession: Villager.Profession,
    override val personalityArchetype: String? = null,
    override val currentRole: String? = null,
    override val hungerLevel: Double? = null,
    override val energyLevel: Double? = null,
    override val villageName: String? = null,
    override val villagePopulation: Int? = null,
    override val villageFoodStock: Int? = null,
    override val villagePendingMaterials: Map<String, Int> = emptyMap(),
    override val recentEvents: List<String> = emptyList(),
    override val relationshipsWithPlayers: Map<UUID, Int> = emptyMap()
) : VillagerContext
