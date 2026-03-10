package io.github.enpici.villager.gpt.conversation.pipeline.actions

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import io.github.enpici.villager.gpt.conversation.pipeline.ConversationMessageAction

class SendPlayerMessageAction(private val player: Player, private val message: Component) : ConversationMessageAction {
    override fun run() {
        player.sendMessage(message)
    }
}