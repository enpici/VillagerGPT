package io.github.enpici.villager.gpt.conversation.pipeline.actions

import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import io.github.enpici.villager.gpt.conversation.pipeline.ConversationMessageAction

class PathfindToPlayerAction(
    private val villager: Villager,
    private val player: Player,
    private val speed: Double
) : ConversationMessageAction {
    override fun run() {
        villager.pathfinder.moveTo(player, speed)
    }
}
