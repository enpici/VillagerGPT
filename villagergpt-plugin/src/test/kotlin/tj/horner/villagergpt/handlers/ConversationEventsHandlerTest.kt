package io.github.enpici.villager.gpt.handlers

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Villager
import org.bukkit.event.entity.EntityDeathEvent
import org.junit.jupiter.api.Test
import io.github.enpici.villager.gpt.VillagerGPT
import io.github.enpici.villager.gpt.conversation.VillagerConversation
import io.github.enpici.villager.gpt.conversation.VillagerConversationManager

class ConversationEventsHandlerTest {

    @Test
    fun `villager death ends active conversation`() {
        val plugin = mockk<VillagerGPT>(relaxed = true)
        val manager = mockk<VillagerConversationManager>(relaxed = true)
        val villager = mockk<Villager>(relaxed = true)
        val conversation = mockk<VillagerConversation>(relaxed = true)
        val event = mockk<EntityDeathEvent>(relaxed = true)

        every { plugin.conversationManager } returns manager
        every { event.entity } returns villager
        every { manager.getConversation(villager) } returns conversation

        ConversationEventsHandler(plugin).onVillagerDied(event)

        verify(exactly = 1) { manager.endConversation(conversation) }
    }

    @Test
    fun `non villager death does not end conversations`() {
        val plugin = mockk<VillagerGPT>(relaxed = true)
        val manager = mockk<VillagerConversationManager>(relaxed = true)
        val event = mockk<EntityDeathEvent>(relaxed = true)
        val entity = mockk<LivingEntity>(relaxed = true)

        every { plugin.conversationManager } returns manager
        every { event.entity } returns entity

        ConversationEventsHandler(plugin).onVillagerDied(event)

        verify(exactly = 0) { manager.endConversation(any()) }
    }
}
