package tj.horner.villagergpt.events

import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class VillagerLLMResponseEvent(
    val player: Player,
    val villager: Villager,
    val conversationId: String,
    val response: String
) : Event(true) {
    companion object {
        private val handlers = HandlerList()

        @Suppress("unused")
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }

    override fun getHandlers(): HandlerList {
        return handlers
    }
}

