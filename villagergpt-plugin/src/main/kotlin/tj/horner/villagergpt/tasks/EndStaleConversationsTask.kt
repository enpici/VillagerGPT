package io.github.enpici.villager.gpt.tasks

import org.bukkit.scheduler.BukkitRunnable
import io.github.enpici.villager.gpt.VillagerGPT

class EndStaleConversationsTask(private val plugin: VillagerGPT) : BukkitRunnable() {
    override fun run() {
        plugin.conversationManager.endStaleConversations()
    }
}