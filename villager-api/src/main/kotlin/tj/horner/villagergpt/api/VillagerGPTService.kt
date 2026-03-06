package tj.horner.villagergpt.api

import org.bukkit.entity.Player
import org.bukkit.entity.Villager

fun interface VillagerDialogueAction {
    fun execute()
}

interface VillagerGPTService {
    fun startConversation(player: Player, villager: Villager): Boolean
    suspend fun generateDialogue(villager: Villager, player: Player, message: String): Iterable<VillagerDialogueAction>
    fun notifyEvent(villager: Villager, eventDescription: String)
}
