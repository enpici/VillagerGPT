package io.github.enpici.villager.gpt.events

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import io.github.enpici.villager.gpt.conversation.VillagerConversation

@OptIn(BetaOpenAI::class)
class VillagerConversationMessageEvent(val conversation: VillagerConversation, val message: ChatMessage) : Event(true) {
    companion object {
        private val handlers = HandlerList()

        @Suppress("unused")
        @JvmStatic
        fun getHandlerList() = handlers
    }

    override fun getHandlers(): HandlerList {
        return VillagerConversationMessageEvent.handlers
    }
}