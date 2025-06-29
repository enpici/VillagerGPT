package tj.horner.villagergpt.memory

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
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
        }
    }

    fun loadMessages(uuid: UUID, limit: Int): List<ChatMessage> {
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
        return messages
    }

    fun appendMessages(uuid: UUID, messages: List<ChatMessage>, maxMessages: Int) {
        if (messages.isEmpty()) return
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

    fun close() {
        connection.close()
    }
}
