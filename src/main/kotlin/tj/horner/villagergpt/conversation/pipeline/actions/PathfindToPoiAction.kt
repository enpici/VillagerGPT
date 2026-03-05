package tj.horner.villagergpt.conversation.pipeline.actions

import org.bukkit.Location
import org.bukkit.entity.Villager
import tj.horner.villagergpt.conversation.pipeline.ConversationMessageAction

class PathfindToPoiAction(
    private val villager: Villager,
    private val destinationProvider: () -> Location?,
    private val speed: Double
) : ConversationMessageAction {
    override fun run() {
        val destination = destinationProvider() ?: return
        villager.pathfinder.moveTo(destination, speed)
    }
}
