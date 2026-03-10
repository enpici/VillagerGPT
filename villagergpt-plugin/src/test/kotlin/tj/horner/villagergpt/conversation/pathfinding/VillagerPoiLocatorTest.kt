package io.github.enpici.villager.gpt.conversation.pathfinding

import org.bukkit.Material
import org.bukkit.entity.Villager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VillagerPoiLocatorTest {
    @Test
    fun `returns expected workstation for librarian`() {
        val workstations = VillagerPoiLocator.workstationForProfession(Villager.Profession.LIBRARIAN)

        assertEquals(setOf(Material.LECTERN), workstations)
    }

    @Test
    fun `returns no workstation for nitwit`() {
        val workstations = VillagerPoiLocator.workstationForProfession(Villager.Profession.NITWIT)

        assertTrue(workstations.isEmpty())
    }
}
