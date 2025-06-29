package tj.horner.villagergpt.conversation

import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import tj.horner.villagergpt.VillagerGPT
import kotlinx.coroutines.runBlocking
import tj.horner.villagergpt.events.VillagerConversationEndEvent
import tj.horner.villagergpt.events.VillagerConversationStartEvent
import com.aallam.openai.api.BetaOpenAI

@OptIn(BetaOpenAI::class)
class VillagerConversationManager(private val plugin: VillagerGPT) {

    private val conversations: MutableList<VillagerConversation> = mutableListOf()

    fun getActiveConversations(): List<VillagerConversation> {
        return conversations.toList()
    }

    fun endStaleConversations() {
        val staleConversations = conversations.filter {
            it.villager.isDead ||
            !it.player.isOnline ||
            it.hasExpired() ||
            it.hasPlayerLeft()
        }

        endConversations(staleConversations)
    }

    fun endAllConversations() {
        endConversations(conversations)
    }

    fun getConversation(player: Player): VillagerConversation? {
        return conversations.firstOrNull { it.player.uniqueId == player.uniqueId }
    }

    fun getConversation(villager: Villager): VillagerConversation? {
        return conversations.firstOrNull { it.villager.uniqueId == villager.uniqueId }
    }

    fun startConversation(player: Player, villager: Villager): VillagerConversation? {
        if (getConversation(player) != null || getConversation(villager) != null)
            return null

        return getConversation(player, villager)
    }

    private fun getConversation(player: Player, villager: Villager): VillagerConversation {
        var conversation = conversations.firstOrNull { it.player.uniqueId == player.uniqueId && it.villager.uniqueId == villager.uniqueId }

        if (conversation == null) {
            conversation = VillagerConversation(plugin, villager, player)
            conversations.add(conversation)

            val startEvent = VillagerConversationStartEvent(conversation)
            plugin.server.pluginManager.callEvent(startEvent)
        }

        return conversation
    }

    fun endConversation(conversation: VillagerConversation) {
        endConversations(listOf(conversation))
    }

    private fun endConversations(conversationsToEnd: Collection<VillagerConversation>) {
        conversationsToEnd.forEach {
            val history = it.messages.drop(1)

            runBlocking {
                plugin.memory.appendMessages(it.villager.uniqueId, history, plugin.config.getInt("max-stored-messages", 20))
                val summary = summarize(history)
                plugin.memory.updateVillagerSummary(it.villager.uniqueId, summary)
            }

            it.ended = true
            val endEvent = VillagerConversationEndEvent(it.player, it.villager)
            plugin.server.pluginManager.callEvent(endEvent)
        }

        conversations.removeAll(conversationsToEnd)
    }

    private fun summarize(history: List<com.aallam.openai.api.chat.ChatMessage>): String {
        val text = history.takeLast(10).joinToString(" ") { it.content }
        return if (text.length > 200) text.substring(0, 200) else text
    }
}