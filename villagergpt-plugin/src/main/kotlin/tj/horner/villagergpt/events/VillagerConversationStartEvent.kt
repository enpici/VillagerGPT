package io.github.enpici.villager.gpt.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import io.github.enpici.villager.gpt.conversation.VillagerConversation

class VillagerConversationStartEvent(val conversation: VillagerConversation) : Event() {
    companion object {
        private val handlers = HandlerList()

        @Suppress("unused")
        @JvmStatic
        fun getHandlerList() = handlers
    }

    override fun getHandlers(): HandlerList {
        return VillagerConversationStartEvent.handlers
    }
}