package io.github.enpici.villager.gpt.observability

import org.bukkit.configuration.file.FileConfiguration
import io.github.enpici.villager.gpt.conversation.VillagerConversation
import java.util.Locale
import java.util.logging.Level

data class ObservabilitySettings(
    val rootLogLevel: Level,
    val contextLogLevel: Level,
    val summaryLogLevel: Level,
    val summaryIntervalTicks: Long
)

data class ConversationLogContext(
    val playerId: String,
    val villagerId: String,
    val provider: String,
    val conversationId: String
) {
    fun asFields(): String = "playerId=$playerId villagerId=$villagerId provider=$provider conversationId=$conversationId"
}

fun VillagerConversation.logContext(provider: String): ConversationLogContext {
    return ConversationLogContext(
        playerId = player.uniqueId.toString(),
        villagerId = villager.uniqueId.toString(),
        provider = provider,
        conversationId = conversationId
    )
}

fun readObservabilitySettings(config: FileConfiguration): ObservabilitySettings {
    return ObservabilitySettings(
        rootLogLevel = readLevel(config.getString("logging.level"), Level.INFO),
        contextLogLevel = readLevel(config.getString("logging.context-level"), Level.INFO),
        summaryLogLevel = readLevel(config.getString("logging.summary-level"), Level.INFO),
        summaryIntervalTicks = config.getLong("logging.summary-interval-ticks", 1200L)
    )
}

private fun readLevel(raw: String?, defaultLevel: Level): Level {
    if (raw.isNullOrBlank()) return defaultLevel

    return runCatching { Level.parse(raw.uppercase(Locale.ROOT)) }
        .getOrElse { defaultLevel }
}
