package tj.horner.villagergpt.conversation

import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import com.aallam.openai.api.BetaOpenAI
import tj.horner.villagergpt.VillagerGPT
import tj.horner.villagergpt.events.VillagerConversationEndEvent
import tj.horner.villagergpt.events.VillagerConversationStartEvent

@OptIn(BetaOpenAI::class)
class VillagerConversationManager(private val plugin: VillagerGPT) {
    private val conversations: MutableList<VillagerConversation> = mutableListOf()

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
            plugin.memory.saveMessages(it.villager.uniqueId, it.messages)
            it.ended = true
            val endEvent = VillagerConversationEndEvent(it.player, it.villager)
            plugin.server.pluginManager.callEvent(endEvent)
        }

        conversations.removeAll(conversationsToEnd)
    }
}