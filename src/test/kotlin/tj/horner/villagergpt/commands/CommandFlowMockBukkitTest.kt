package tj.horner.villagergpt.commands

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.ServerMock
import be.seeseemelk.mockbukkit.entity.PlayerMock
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.bukkit.command.Command
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tj.horner.villagergpt.MetadataKey
import tj.horner.villagergpt.VillagerGPT
import tj.horner.villagergpt.conversation.VillagerConversation
import tj.horner.villagergpt.conversation.VillagerConversationManager

class CommandFlowMockBukkitTest {
    private lateinit var server: ServerMock
    private lateinit var player: PlayerMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        player = server.addPlayer()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `ttv sets selecting metadata when player is not in conversation`() = runBlocking {
        val plugin = mockk<VillagerGPT>(relaxed = true)
        val conversationManager = mockk<VillagerConversationManager>(relaxed = true)
        val command = mockk<Command>(relaxed = true)

        every { plugin.conversationManager } returns conversationManager
        every { conversationManager.getConversation(player) } returns null

        val result = TalkCommand(plugin).onCommand(player, command, "ttv", emptyArray())

        assertTrue(result)
        assertTrue(player.hasMetadata(MetadataKey.SelectingVillager))
    }

    @Test
    fun `ttvclear resets current conversation`() = runBlocking {
        val plugin = mockk<VillagerGPT>(relaxed = true)
        val conversationManager = mockk<VillagerConversationManager>(relaxed = true)
        val command = mockk<Command>(relaxed = true)
        val conversation = mockk<VillagerConversation>(relaxed = true)

        every { plugin.conversationManager } returns conversationManager
        every { conversationManager.getConversation(player) } returns conversation

        val result = ClearCommand(plugin).onCommand(player, command, "ttvclear", emptyArray())

        assertTrue(result)
        verify(exactly = 1) { conversation.reset() }
    }

    @Test
    fun `ttvend closes active conversation`() = runBlocking {
        val plugin = mockk<VillagerGPT>(relaxed = true)
        val conversationManager = mockk<VillagerConversationManager>(relaxed = true)
        val command = mockk<Command>(relaxed = true)
        val conversation = mockk<VillagerConversation>(relaxed = true)

        every { plugin.conversationManager } returns conversationManager
        every { conversationManager.getConversation(player) } returns conversation

        val result = EndCommand(plugin).onCommand(player, command, "ttvend", emptyArray())

        assertTrue(result)
        verify(exactly = 1) { conversationManager.endConversation(conversation) }
        assertFalse(player.hasMetadata(MetadataKey.SelectingVillager))
    }
}
