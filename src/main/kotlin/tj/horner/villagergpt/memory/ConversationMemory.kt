package tj.horner.villagergpt.memory

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

@OptIn(BetaOpenAI::class)
class ConversationMemory(dbFile: String) {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$dbFile")

    init {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    villager_uuid TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL
                )
                """.trimIndent()
            )
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_villager_uuid ON messages(villager_uuid)")

            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS gossip (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    villager_uuid TEXT NOT NULL,
                    content TEXT NOT NULL
                )
                """.trimIndent()
            )
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_gossip_villager_uuid ON gossip(villager_uuid)")
        }
    }

    suspend fun loadMessages(uuid: UUID, limit: Int): List<ChatMessage> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<Pair<String, String>>()
        connection.prepareStatement("SELECT role, content FROM messages WHERE villager_uuid=? ORDER BY id DESC LIMIT ?").use { ps ->
            ps.setString(1, uuid.toString())
            ps.setInt(2, limit)
            val rs = ps.executeQuery()
            while (rs.next()) {
                rows.add(rs.getString("role") to rs.getString("content"))
            }
        }

        val messages = mutableListOf<ChatMessage>()
        for (i in rows.size - 1 downTo 0) {
            val (role, content) = rows[i]
            messages.add(ChatMessage(role = ChatRole(role), content = content))
        }
        messages
    }

    suspend fun appendMessages(uuid: UUID, messages: List<ChatMessage>, maxMessages: Int) = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext
        connection.prepareStatement("INSERT INTO messages (villager_uuid, role, content) VALUES (?, ?, ?)").use { ps ->
            for (msg in messages) {
                ps.setString(1, uuid.toString())
                ps.setString(2, msg.role.role)
                ps.setString(3, msg.content)
                ps.addBatch()
            }
            ps.executeBatch()
        }

        connection.prepareStatement(
            "DELETE FROM messages WHERE villager_uuid=? AND id NOT IN (SELECT id FROM messages WHERE villager_uuid=? ORDER BY id DESC LIMIT ?)"
        ).use { ps ->
            ps.setString(1, uuid.toString())
            ps.setString(2, uuid.toString())
            ps.setInt(3, maxMessages)
            ps.executeUpdate()
        }
    }

    fun loadGossip(uuid: UUID, limit: Int): List<String> {
        val gossip = mutableListOf<String>()
        connection.prepareStatement("SELECT content FROM gossip WHERE villager_uuid=? ORDER BY id DESC LIMIT ?").use { ps ->
            ps.setString(1, uuid.toString())
            ps.setInt(2, limit)
            val rs = ps.executeQuery()
            while (rs.next()) {
                gossip.add(rs.getString("content"))
            }
        }
        return gossip.reversed()
    }

    fun addGossip(uuid: UUID, entries: List<String>, maxEntries: Int) {
        if (entries.isEmpty()) return
        connection.prepareStatement("INSERT INTO gossip (villager_uuid, content) VALUES (?, ?)").use { ps ->
            for (entry in entries) {
                ps.setString(1, uuid.toString())
                ps.setString(2, entry)
                ps.addBatch()
            }
            ps.executeBatch()
        }

        connection.prepareStatement(
            "DELETE FROM gossip WHERE villager_uuid=? AND id NOT IN (SELECT id FROM gossip WHERE villager_uuid=? ORDER BY id DESC LIMIT ?)"
        ).use { ps ->
            ps.setString(1, uuid.toString())
            ps.setString(2, uuid.toString())
            ps.setInt(3, maxEntries)
            ps.executeUpdate()
        }
    }

    fun close() {
        connection.close()
    }
}
