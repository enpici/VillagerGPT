package io.github.enpici.villager.gpt.conversation.pipeline.actions

import org.bukkit.entity.Villager
import io.github.enpici.villager.gpt.conversation.pipeline.ConversationMessageAction

class ShakeHeadAction(private val villager: Villager) : ConversationMessageAction {
    override fun run() {
        villager.shakeHead()
    }
}