package tj.horner.villagergpt.api

import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import tj.horner.villagergpt.conversation.VillagerConversation
import tj.horner.villagergpt.conversation.pipeline.ConversationMessageAction

interface VillagerGPTService {
    fun startConversation(player: Player, villager: Villager): VillagerConversation?
    suspend fun generateDialogue(villager: Villager, player: Player, message: String): Iterable<ConversationMessageAction>
    fun notifyEvent(villager: Villager, eventDescription: String)
}

