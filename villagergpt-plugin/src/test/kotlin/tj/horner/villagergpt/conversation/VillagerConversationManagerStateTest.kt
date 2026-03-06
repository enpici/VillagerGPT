package tj.horner.villagergpt.conversation

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.bukkit.Server
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tj.horner.villagergpt.VillagerGPT
import tj.horner.villagergpt.conversation.pipeline.producers.ProviderMetricsRegistry
import tj.horner.villagergpt.memory.ConversationMemory
import java.util.UUID

@OptIn(BetaOpenAI::class)
class VillagerConversationManagerStateTest {

    @Test
    fun `endConversation persists history and removes conversation from active list`() {
        val memory = mockk<ConversationMemory>(relaxed = true)
        val plugin = mockPlugin(memory)
        val manager = VillagerConversationManager(plugin)

        val player = mockk<Player>(relaxed = true)
        val playerId = UUID.randomUUID()
        every { player.uniqueId } returns playerId

        val villager = mockk<Villager>(relaxed = true)
        val villagerId = UUID.randomUUID()
        every { villager.uniqueId } returns villagerId

        val conversation = mockk<VillagerConversation>(relaxed = true)
        every { conversation.player } returns player
        every { conversation.villager } returns villager
        every { conversation.conversationId } returns "conv-1"
        every { conversation.messages } returns mutableListOf(
            ChatMessage(ChatRole.System, "system"),
            ChatMessage(ChatRole.User, "hola"),
            ChatMessage(ChatRole.Assistant, "que tal")
        )
        every { conversation.pendingResponse = any() } just runs
        every { conversation.ended = any() } just runs

        injectConversation(manager, conversation)

        manager.endConversation(conversation)

        val historySlot = slot<List<ChatMessage>>()
        coVerify(exactly = 1) { memory.appendMessages(villagerId, capture(historySlot), 20) }
        assertEquals(2, historySlot.captured.size)
        assertEquals("hola", historySlot.captured.first().content)

        verify(exactly = 1) { memory.updateVillagerSummary(villagerId, any()) }
        assertTrue(manager.getActiveConversations().isEmpty())
    }

    @Test
    fun `endStaleConversations closes conversations for distance disconnect and villager death`() {
        val memory = mockk<ConversationMemory>(relaxed = true)
        val plugin = mockPlugin(memory)
        val manager = VillagerConversationManager(plugin)

        val aliveConversation = conversation(
            id = "active",
            playerOnline = true,
            villagerDead = false,
            expired = false,
            playerLeft = false
        )
        val distanceCutConversation = conversation(
            id = "distance",
            playerOnline = true,
            villagerDead = false,
            expired = false,
            playerLeft = true
        )
        val disconnectConversation = conversation(
            id = "disconnect",
            playerOnline = false,
            villagerDead = false,
            expired = false,
            playerLeft = false
        )
        val deadVillagerConversation = conversation(
            id = "dead",
            playerOnline = true,
            villagerDead = true,
            expired = false,
            playerLeft = false
        )

        injectConversation(manager, aliveConversation, distanceCutConversation, disconnectConversation, deadVillagerConversation)

        manager.endStaleConversations()

        val remaining = manager.getActiveConversations().map { it.conversationId }
        assertEquals(listOf("active"), remaining)
        coVerify(exactly = 3) { memory.appendMessages(any(), any(), 20) }
        verify(exactly = 3) { memory.updateVillagerSummary(any(), any()) }
    }

    private fun mockPlugin(memory: ConversationMemory): VillagerGPT {
        val config = mockk<FileConfiguration>(relaxed = true)
        every { config.getInt("memory.max-messages", 20) } returns 20

        val pluginManager = mockk<PluginManager>(relaxed = true)
        every { pluginManager.callEvent(any()) } returns Unit

        val server = mockk<Server>(relaxed = true)
        every { server.pluginManager } returns pluginManager

        val metrics = mockk<ProviderMetricsRegistry>(relaxed = true)

        val plugin = mockk<VillagerGPT>(relaxed = true)
        every { plugin.memory } returns memory
        every { plugin.server } returns server
        every { plugin.config } returns config
        every { plugin.providerMetrics } returns metrics
        return plugin
    }

    private fun conversation(
        id: String,
        playerOnline: Boolean,
        villagerDead: Boolean,
        expired: Boolean,
        playerLeft: Boolean
    ): VillagerConversation {
        val player = mockk<Player>(relaxed = true)
        val playerId = UUID.randomUUID()
        every { player.uniqueId } returns playerId
        every { player.isOnline } returns playerOnline

        val villager = mockk<Villager>(relaxed = true)
        val villagerId = UUID.randomUUID()
        every { villager.uniqueId } returns villagerId
        every { villager.isDead } returns villagerDead

        return mockk(relaxed = true) {
            every { this@mockk.player } returns player
            every { this@mockk.villager } returns villager
            every { conversationId } returns id
            every { hasExpired() } returns expired
            every { hasPlayerLeft() } returns playerLeft
            every { messages } returns mutableListOf(
                ChatMessage(ChatRole.System, "s"),
                ChatMessage(ChatRole.User, id)
            )
            every { pendingResponse = any() } just runs
            every { ended = any() } just runs
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun injectConversation(manager: VillagerConversationManager, vararg conversation: VillagerConversation) {
        val field = VillagerConversationManager::class.java.getDeclaredField("conversations")
        field.isAccessible = true
        val activeConversations = field.get(manager) as MutableList<VillagerConversation>
        activeConversations.addAll(conversation)
    }
}
