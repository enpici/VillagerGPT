package io.github.enpici.villager.gpt.conversation.pipeline.processors

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import io.github.enpici.villager.gpt.conversation.VillagerConversation
import io.github.enpici.villager.gpt.conversation.pipeline.ConversationMessageAction
import io.github.enpici.villager.gpt.conversation.pipeline.ConversationMessageProcessor
import io.github.enpici.villager.gpt.conversation.pipeline.ConversationMessageTransformer
import io.github.enpici.villager.gpt.conversation.pathfinding.VillagerPoiLocator
import io.github.enpici.villager.gpt.conversation.pipeline.actions.PathfindToPlayerAction
import io.github.enpici.villager.gpt.conversation.pipeline.actions.PathfindToPoiAction
import io.github.enpici.villager.gpt.conversation.pipeline.actions.PlaySoundAction
import io.github.enpici.villager.gpt.conversation.pipeline.actions.ShakeHeadAction

class ActionProcessor : ConversationMessageProcessor, ConversationMessageTransformer {
    private val actionRegex = Regex("ACTION:([A-Z_]+)")
    private val poiSearchRadius = 32
    private val pathSpeed = 1.0

    override fun processMessage(
        message: String,
        conversation: VillagerConversation
    ): Collection<ConversationMessageAction> {
        val parsedActions = getActions(message)
        return parsedActions.mapNotNull { textToAction(it, conversation) }
    }

    override fun transformMessage(message: String, conversation: VillagerConversation): String {
        return message.replace(actionRegex, "").trim()
    }

    private fun getActions(message: String): Set<String> {
        val matches = actionRegex.findAll(message)
        return matches.map { it.groupValues[1] }.toSet()
    }

    private fun textToAction(text: String, conversation: VillagerConversation): ConversationMessageAction? {
        return when (text) {
            "SHAKE_HEAD" -> ShakeHeadAction(conversation.villager)
            "SOUND_YES" -> PlaySoundAction(conversation.player, conversation.villager, villagerSound("entity.villager.yes"))
            "SOUND_NO" -> PlaySoundAction(conversation.player, conversation.villager, villagerSound("entity.villager.no"))
            "SOUND_AMBIENT" -> PlaySoundAction(conversation.player, conversation.villager, villagerSound("entity.villager.ambient"))
            "PATHFIND_PLAYER" -> PathfindToPlayerAction(conversation.villager, conversation.player, pathSpeed)
            "PATHFIND_BED" -> PathfindToPoiAction(
                conversation.villager,
                destinationProvider = { VillagerPoiLocator.nearestBed(conversation.villager, poiSearchRadius) },
                speed = pathSpeed
            )
            "PATHFIND_WORKSTATION" -> PathfindToPoiAction(
                conversation.villager,
                destinationProvider = { VillagerPoiLocator.nearestWorkstation(conversation.villager, poiSearchRadius) },
                speed = pathSpeed
            )
            "PATHFIND_MEETING_POINT" -> PathfindToPoiAction(
                conversation.villager,
                destinationProvider = { VillagerPoiLocator.nearestMeetingPoint(conversation.villager, poiSearchRadius) },
                speed = pathSpeed
            )
            else -> null
        }
    }

    private fun villagerSound(key: String): Sound {
        return Sound.sound(Key.key(key), Sound.Source.NEUTRAL, 1f, 1f)
    }
}