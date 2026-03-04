package tj.horner.villagergpt.memory

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.UUID

@OptIn(BetaOpenAI::class)
class ConversationMemoryTest {

    @Test
    fun `appendMessages prunes to configured max`() = runBlocking {
        val db = Files.createTempFile("villager-memory", ".db")
        val memory = ConversationMemory(db.toString())
        val villagerId = UUID.randomUUID()

        try {
            memory.appendMessages(
                villagerId,
                listOf(
                    ChatMessage(ChatRole.User, "m1"),
                    ChatMessage(ChatRole.Assistant, "m2"),
                    ChatMessage(ChatRole.User, "m3")
                ),
                maxMessages = 2
            )

            val loaded = memory.loadMessages(villagerId, 10)
            assertEquals(2, loaded.size)
            assertEquals("m2", loaded[0].content)
            assertEquals("m3", loaded[1].content)
        } finally {
            memory.close()
            Files.deleteIfExists(db)
        }
    }

    @Test
    fun `addGossip respects pruning limit boundary`() {
        val db = Files.createTempFile("villager-memory", ".db")
        val memory = ConversationMemory(db.toString())
        val villagerId = UUID.randomUUID()

        try {
            memory.addGossip(villagerId, listOf("g1", "g2"), maxEntries = 2)
            assertEquals(listOf("g1", "g2"), memory.loadGossip(villagerId, 10))

            memory.addGossip(villagerId, listOf("g3"), maxEntries = 2)
            assertEquals(listOf("g2", "g3"), memory.loadGossip(villagerId, 10))
        } finally {
            memory.close()
            Files.deleteIfExists(db)
        }
    }
}
