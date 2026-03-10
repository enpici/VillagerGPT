package io.github.enpici.villager.gpt.conversation.pipeline.actions

import net.kyori.adventure.sound.Sound
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import io.github.enpici.villager.gpt.conversation.pipeline.ConversationMessageAction

class PlaySoundAction(private val player: Player, private val entity: Entity, private val sound: Sound) : ConversationMessageAction {
    override fun run() {
        player.playSound(sound, entity)
    }
}