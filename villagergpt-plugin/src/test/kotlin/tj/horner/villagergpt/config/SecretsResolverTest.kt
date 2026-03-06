package tj.horner.villagergpt.config

import io.mockk.every
import io.mockk.mockk
import org.bukkit.configuration.Configuration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SecretsResolverTest {
    @Test
    fun `uses legacy config key when env var is missing`() {
        val config = mockk<Configuration>()
        every { config.getString("openai-key-env-var") } returns "VGPT_TEST_ENV_THAT_SHOULD_NOT_EXIST"
        every { config.getString("openai-key") } returns "legacy-key"

        assertEquals("legacy-key", SecretsResolver.resolveOpenAiKey(config))
    }

    @Test
    fun `returns null when no key source is available`() {
        val config = mockk<Configuration>()
        every { config.getString("openai-key-env-var") } returns "VGPT_TEST_ENV_THAT_SHOULD_NOT_EXIST"
        every { config.getString("openai-key") } returns "   "

        assertNull(SecretsResolver.resolveOpenAiKey(config))
    }
}
