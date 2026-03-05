package tj.horner.villagergpt.conversation.pipeline.actions

import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import tj.horner.villagergpt.conversation.pipeline.ConversationMessageAction

class PathfindToPlayerAction(
    private val villager: Villager,
    private val player: Player,
    private val speed: Double
) : ConversationMessageAction {
    override fun run() {
        villager.pathfinder.moveTo(player, speed)
    }
}
