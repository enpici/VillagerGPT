package io.github.enpici.villager.gpt.handlers

import com.aallam.openai.api.BetaOpenAI
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import io.github.enpici.villager.gpt.MetadataKey
import io.github.enpici.villager.gpt.VillagerGPT
import io.github.enpici.villager.gpt.chat.ChatMessageTemplate
import io.github.enpici.villager.gpt.conversation.VillagerConversation
import io.github.enpici.villager.gpt.conversation.formatting.MessageFormatter
import io.github.enpici.villager.gpt.conversation.pipeline.producers.ProviderException
import io.github.enpici.villager.gpt.events.VillagerConversationEndEvent
import io.github.enpici.villager.gpt.events.VillagerConversationMessageEvent
import io.github.enpici.villager.gpt.events.VillagerConversationStartEvent
import io.github.enpici.villager.gpt.observability.ConversationLogContext
import io.github.enpici.villager.gpt.observability.logContext

class ConversationEventsHandler(private val plugin: VillagerGPT) : Listener {
    private companion object {
        const val PROVIDER_UNKNOWN = "unknown"
        const val ERROR_PROVIDER_FAILED = "VGPT-CONV-001"
        const val ERROR_UNEXPECTED = "VGPT-CONV-002"
    }

    @EventHandler
    fun onConversationStart(evt: VillagerConversationStartEvent) {
        val message = Component.text("You are now in a conversation with ")
            .append(evt.conversation.villager.name().color(NamedTextColor.AQUA))
            .append(Component.text(". Send a chat message to get started and use /ttvend to end it"))
            .decorate(TextDecoration.ITALIC)

        evt.conversation.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))

        if (!plugin.config.getBoolean("villagers-aware-during-conversation")) {
            evt.conversation.villager.isAware = false
        }
        evt.conversation.villager.lookAt(evt.conversation.player)
        val context = evt.conversation.logContext(plugin.providerName())
        plugin.logger.log(plugin.observabilitySettings.contextLogLevel, "conversation_started ${context.asFields()}")
    }

    @EventHandler
    fun onConversationEnd(evt: VillagerConversationEndEvent) {
        val message = Component.text("Your conversation with ")
            .append(evt.villager.name().color(NamedTextColor.AQUA))
            .append(Component.text(" has ended"))
            .decorate(TextDecoration.ITALIC)

        evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))

        evt.villager.resetOffers()
        if (!plugin.config.getBoolean("villagers-aware-during-conversation")) {
            evt.villager.isAware = true
        }
        val context = ConversationLogContext(
            playerId = evt.player.uniqueId.toString(),
            villagerId = evt.villager.uniqueId.toString(),
            provider = plugin.providerName(),
            conversationId = evt.conversationId
        )
        plugin.logger.log(plugin.observabilitySettings.contextLogLevel, "conversation_ended ${context.asFields()}")
    }

    @EventHandler
    fun onVillagerInteracted(evt: PlayerInteractEntityEvent) {
        if (evt.rightClicked !is Villager) return
        val villager = evt.rightClicked as Villager

        // Villager is in a conversation with another player
        val existingConversation = plugin.conversationManager.getConversation(villager)
        if (existingConversation != null && existingConversation.player.uniqueId != evt.player.uniqueId) {
            val message = Component.text("This villager is in a conversation with ")
                .append(existingConversation.player.displayName())
                .decorate(TextDecoration.ITALIC)

            evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))
            evt.isCancelled = true
            return
        }

        if (!evt.player.hasMetadata(MetadataKey.SelectingVillager)) return

        // Player is selecting a villager for conversation
        evt.isCancelled = true

        if (villager.profession == Villager.Profession.NONE) {
            val message = Component.text("You can only speak to villagers with a profession")
                .decorate(TextDecoration.ITALIC)

            evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))
            return
        }

        plugin.conversationManager.startConversation(evt.player, villager)
        evt.player.removeMetadata(MetadataKey.SelectingVillager, plugin)
    }

    private fun logThrottleEvent(conversation: VillagerConversation, rule: String, details: String) {
        val context = conversation.logContext(plugin.providerName())
        plugin.logger.log(
            plugin.observabilitySettings.contextLogLevel,
            "conversation_throttled ${context.asFields()} rule=$rule $details"
        )
    }

    @EventHandler
    suspend fun onSendMessage(evt: AsyncChatEvent) {
        val conversation = plugin.conversationManager.getConversation(evt.player) ?: return
        evt.isCancelled = true

        if (conversation.pendingResponse) {
            val message = Component.text("Please wait for ")
                .append(conversation.villager.name().color(NamedTextColor.AQUA))
                .append(Component.text(" to respond"))
                .decorate(TextDecoration.ITALIC)

            evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))
            return
        }

        val playerMessage = PlainTextComponentSerializer.plainText().serialize(evt.originalMessage()).trim()
        val safety = plugin.conversationSafetySettings
        val now = System.currentTimeMillis()
        val elapsed = now - conversation.lastPlayerMessageAtMs

        if (conversation.lastPlayerMessageAtMs != 0L && elapsed < safety.playerCooldownMs) {
            val waitMs = safety.playerCooldownMs - elapsed
            evt.player.sendMessage(
                ChatMessageTemplate.withPluginNamePrefix(
                    Component.text("Slow down. Wait ${waitMs}ms before your next message.")
                        .decorate(TextDecoration.ITALIC)
                )
            )
            logThrottleEvent(conversation, "cooldown", "waitMs=$waitMs")
            return
        }

        if (playerMessage.isEmpty() || playerMessage.length > safety.maxInputLength) {
            evt.player.sendMessage(
                ChatMessageTemplate.withPluginNamePrefix(
                    Component.text("Message length must be between 1 and ${safety.maxInputLength} characters.")
                        .decorate(TextDecoration.ITALIC)
                )
            )
            logThrottleEvent(conversation, "input_length", "size=${playerMessage.length} max=${safety.maxInputLength}")
            return
        }

        if (conversation.playerMessagesInSession >= safety.sessionMaxPlayerMessages) {
            evt.player.sendMessage(
                ChatMessageTemplate.withPluginNamePrefix(
                    Component.text("Session message budget exceeded. End this conversation and start a new one.")
                        .decorate(TextDecoration.ITALIC)
                )
            )
            logThrottleEvent(
                conversation,
                "session_message_budget",
                "count=${conversation.playerMessagesInSession} max=${safety.sessionMaxPlayerMessages}"
            )
            return
        }

        if (conversation.playerCharsInSession + playerMessage.length > safety.sessionMaxPlayerChars) {
            evt.player.sendMessage(
                ChatMessageTemplate.withPluginNamePrefix(
                    Component.text("Session character budget exceeded. End this conversation and start a new one.")
                        .decorate(TextDecoration.ITALIC)
                )
            )
            logThrottleEvent(
                conversation,
                "session_char_budget",
                "used=${conversation.playerCharsInSession} incoming=${playerMessage.length} max=${safety.sessionMaxPlayerChars}"
            )
            return
        }

        conversation.pendingResponse = true
        conversation.lastPlayerMessageAtMs = now
        conversation.playerMessagesInSession += 1
        conversation.playerCharsInSession += playerMessage.length

        try {
            val pipeline = plugin.messagePipeline

            val formattedPlayerMessage = MessageFormatter.formatMessageFromPlayer(Component.text(playerMessage), conversation.villager)

            evt.player.sendMessage(formattedPlayerMessage)

            val actions = pipeline.run(playerMessage, conversation)
            if (!conversation.ended) {
                withContext(plugin.minecraftDispatcher) {
                    actions.forEach { it.run() }
                }
            }
        } catch(e: ProviderException) {
            notifyPlayerAndCloseConversation(
                evt = evt,
                conversation = conversation,
                userMessage = e.fallbackMessage,
                provider = e.provider,
                errorCode = ERROR_PROVIDER_FAILED,
                details = "recoverable=${e.recoverable} cause=${e.cause?.message}"
            )
            plugin.logger.log(plugin.observabilitySettings.contextLogLevel, "provider_error_metrics errorCode=$ERROR_PROVIDER_FAILED ${plugin.providerMetrics.snapshot(e.provider)}")
        } catch(e: Exception) {
            notifyPlayerAndCloseConversation(
                evt = evt,
                conversation = conversation,
                userMessage = "Something went wrong while getting a response. Please try again",
                provider = PROVIDER_UNKNOWN,
                errorCode = ERROR_UNEXPECTED,
                details = "cause=${e.message} type=${e::class.simpleName}"
            )
        } finally {
            conversation.pendingResponse = false
        }
    }

    private suspend fun notifyPlayerAndCloseConversation(
        evt: AsyncChatEvent,
        conversation: VillagerConversation,
        userMessage: String,
        provider: String,
        errorCode: String,
        details: String
    ) {
        val message = Component.text("$userMessage (code: $errorCode)")
            .decorate(TextDecoration.ITALIC)

        val context = conversation.logContext(provider)
        plugin.logger.warning("conversation_send_failed errorCode=$errorCode ${context.asFields()} $details")

        evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))

        withContext(plugin.minecraftDispatcher) {
            if (!conversation.ended) {
                plugin.conversationManager.endConversation(conversation)
            }
        }
    }

    @OptIn(BetaOpenAI::class)
    @EventHandler
    fun onConversationMessage(evt: VillagerConversationMessageEvent) {
        plugin.providerMetrics.recordConversationMessage(
            evt.conversation.conversationId,
            evt.message.content.length
        )
        if (!plugin.config.getBoolean("log-conversations")) return
        val context = evt.conversation.logContext(plugin.providerName())
        plugin.logger.log(plugin.observabilitySettings.contextLogLevel, "conversation_message ${context.asFields()} role=${evt.message.role.role} size=${evt.message.content.length} payload=${evt.message}")
    }

    @EventHandler
    fun onVillagerDied(evt: EntityDeathEvent) {
        if (evt.entity !is Villager) return
        val villager = evt.entity as Villager

        val conversation = plugin.conversationManager.getConversation(villager)
        if (conversation != null) {
            plugin.conversationManager.endConversation(conversation)
        }
    }
}
