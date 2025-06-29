package tj.horner.villagergpt.tasks

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import org.bukkit.entity.Entity
import org.bukkit.scheduler.BukkitRunnable
import tj.horner.villagergpt.VillagerGPT
import tj.horner.villagergpt.conversation.VillagerConversation

@OptIn(BetaOpenAI::class)
class EnvironmentWatcher(private val plugin: VillagerGPT) : BukkitRunnable() {
    private data class EnvironmentState(
        var isDay: Boolean,
        var isRaining: Boolean,
        var nearbyEntities: Set<Int>
    )

    private val state = mutableMapOf<VillagerConversation, EnvironmentState>()

    override fun run() {
        val conversations = plugin.conversationManager.getActiveConversations()
        state.keys.retainAll(conversations)

        conversations.forEach { convo ->
            val villager = convo.villager
            val world = villager.world
            val prev = state.getOrPut(convo) {
                EnvironmentState(world.isDayTime, world.hasStorm(), emptySet())
            }

            // Time change
            val isDay = world.isDayTime
            if (prev.isDay != isDay) {
                val msg = if (isDay) "The sun rises." else "The sun sets."
                convo.addMessage(ChatMessage(ChatRole.System, msg))
                prev.isDay = isDay
            }

            // Weather change
            val raining = world.hasStorm()
            if (prev.isRaining != raining) {
                val msg = if (raining) "Rain begins to fall." else "The rain stops."
                convo.addMessage(ChatMessage(ChatRole.System, msg))
                prev.isRaining = raining
            }

            // Nearby entity detection
            val radius = 5.0
            val nearby: Set<Int> = world.getNearbyEntities(villager.location, radius, radius, radius)
                .filter { it != villager && it != convo.player }
                .map(Entity::getEntityId)
                .toSet()
            if (!prev.nearbyEntities.containsAll(nearby)) {
                if ((nearby - prev.nearbyEntities).isNotEmpty()) {
                    convo.addMessage(ChatMessage(ChatRole.System, "Something moves nearby."))
                }
            }
            prev.nearbyEntities = nearby
        }
    }
}
