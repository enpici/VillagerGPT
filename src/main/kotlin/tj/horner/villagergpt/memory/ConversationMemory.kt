package tj.horner.villagergpt.memory

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import org.bukkit.plugin.Plugin
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.Base64
import java.util.UUID

@OptIn(BetaOpenAI::class)
class ConversationMemory(plugin: Plugin) {
    private val connection: Connection
    private val maxMessages: Int

    init {
        val dbPath = plugin.config.getString("database-path")
            ?: "${plugin.dataFolder}/memory.db"
        maxMessages = plugin.config.getInt("max-stored-messages", 20)

        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        connection.createStatement().use {
            it.executeUpdate(
                "CREATE TABLE IF NOT EXISTS villager_memory (villager_uuid TEXT PRIMARY KEY, messages TEXT)"
            )
        }
    }

    fun loadMessages(uuid: UUID): List<ChatMessage> {
        val stmt = connection.prepareStatement("SELECT messages FROM villager_memory WHERE villager_uuid = ?")
        stmt.use {
            it.setString(1, uuid.toString())
            val rs = it.executeQuery()
            if (rs.next()) {
                val data = rs.getString("messages") ?: return emptyList()
                val messages = decodeMessages(data)
                return limitMessages(messages)
            }
        }
        return emptyList()
    }

    fun saveMessages(uuid: UUID, messages: List<ChatMessage>) {
        val filtered = limitMessages(messages.filter { it.role != ChatRole.System })
        val data = encodeMessages(filtered)
        val stmt: PreparedStatement = connection.prepareStatement(
            "INSERT INTO villager_memory(villager_uuid, messages) VALUES (?, ?) ON CONFLICT(villager_uuid) DO UPDATE SET messages = excluded.messages"
        )
        stmt.use {
            it.setString(1, uuid.toString())
            it.setString(2, data)
            it.executeUpdate()
        }
    }

    fun close() {
        connection.close()
    }

    private fun limitMessages(messages: List<ChatMessage>): List<ChatMessage> {
        if (maxMessages <= 0) return messages
        return if (messages.size <= maxMessages) messages else messages.takeLast(maxMessages)
    }

    private fun encodeMessages(messages: List<ChatMessage>): String {
        return messages.joinToString("\n") {
            val encoded = Base64.getEncoder().encodeToString(it.content.toByteArray())
            "${it.role}|$encoded"
        }
    }

    private fun decodeMessages(data: String): List<ChatMessage> {
        if (data.isBlank()) return emptyList()
        return data.lines().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split("|", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val roleString = parts[0]
            val role = when(roleString.lowercase()) {
                "system" -> ChatRole.System
                "assistant" -> ChatRole.Assistant
                "user" -> ChatRole.User
                else -> ChatRole(roleString)
            }
            val content = String(Base64.getDecoder().decode(parts[1]))
            ChatMessage(role = role, content = content)
        }
    }
}
