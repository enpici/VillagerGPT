package tj.horner.villagergpt.config

import org.bukkit.configuration.Configuration

data class ConversationSafetySettings(
    val playerCooldownMs: Long,
    val maxInputLength: Int,
    val sessionMaxPlayerMessages: Int,
    val sessionMaxPlayerChars: Int
)

fun readConversationSafetySettings(config: Configuration): ConversationSafetySettings {
    val section = config.getConfigurationSection("safety")
    return ConversationSafetySettings(
        playerCooldownMs = section?.getLong("player-cooldown-ms") ?: 1500L,
        maxInputLength = section?.getInt("max-input-length") ?: 280,
        sessionMaxPlayerMessages = section?.getInt("session-max-player-messages") ?: 24,
        sessionMaxPlayerChars = section?.getInt("session-max-player-chars") ?: 6000
    )
}

