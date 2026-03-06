package tj.horner.villagergpt.config

import io.mockk.every
import io.mockk.mockk
import org.bukkit.configuration.Configuration
import org.bukkit.configuration.ConfigurationSection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConversationSafetySettingsTest {
    @Test
    fun `reads safety settings from config`() {
        val config = mockk<Configuration>()
        val section = mockk<ConfigurationSection>()

        every { config.getConfigurationSection("safety") } returns section
        every { section.getLong("player-cooldown-ms") } returns 2500L
        every { section.getInt("max-input-length") } returns 120
        every { section.getInt("session-max-player-messages") } returns 12
        every { section.getInt("session-max-player-chars") } returns 1000

        val settings = readConversationSafetySettings(config)

        assertEquals(2500L, settings.playerCooldownMs)
        assertEquals(120, settings.maxInputLength)
        assertEquals(12, settings.sessionMaxPlayerMessages)
        assertEquals(1000, settings.sessionMaxPlayerChars)
    }
}
