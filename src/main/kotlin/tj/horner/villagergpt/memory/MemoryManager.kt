package tj.horner.villagergpt.memory

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import org.bukkit.entity.Villager
import org.bukkit.plugin.Plugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.Base64

@OptIn(BetaOpenAI::class)
class MemoryManager(private val plugin: Plugin) {
    private val connection: Connection
    private val maxMessages: Int

    init {
        val path = plugin.config.getString("memory.db-path") ?: "memory.db"
        val dbFile = if (File(path).isAbsolute) File(path) else File(plugin.dataFolder, path)
        dbFile.parentFile.mkdirs()
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.path}")
        connection.createStatement().use { stmt ->
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS memory (villager_uuid TEXT PRIMARY KEY, messages TEXT)"
            )
        }
        maxMessages = plugin.config.getInt("memory.max-messages", 20)
    }

    fun loadMessages(villager: Villager): MutableList<ChatMessage> {
        val stmt: PreparedStatement = connection.prepareStatement("SELECT messages FROM memory WHERE villager_uuid = ?")
        stmt.setString(1, villager.uniqueId.toString())
        val rs = stmt.executeQuery()
        val result = if (rs.next()) {
            val data = rs.getString("messages") ?: ""
            deserializeMessages(data)
        } else {
            mutableListOf()
        }
        rs.close()
        stmt.close()
        return result
    }

    fun saveMessages(villager: Villager, messages: List<ChatMessage>) {
        val trimmed = messages.takeLast(maxMessages)
        val data = serializeMessages(trimmed)
        val stmt: PreparedStatement = connection.prepareStatement(
            "INSERT OR REPLACE INTO memory(villager_uuid, messages) VALUES (?, ?)"
        )
        stmt.setString(1, villager.uniqueId.toString())
        stmt.setString(2, data)
        stmt.executeUpdate()
        stmt.close()
    }

    fun close() {
        connection.close()
    }

    private fun serializeMessages(messages: List<ChatMessage>): String {
        return messages.joinToString("\n") { msg ->
            val encoded = Base64.getEncoder().encodeToString(msg.content.toByteArray(Charsets.UTF_8))
            "${msg.role.role}=$encoded"
        }
    }

    private fun deserializeMessages(data: String): MutableList<ChatMessage> {
        if (data.isBlank()) return mutableListOf()
        return data.split("\n").map { line ->
            val parts = line.split("=", limit = 2)
            val role = ChatRole(parts[0])
            val content = String(Base64.getDecoder().decode(parts[1]), Charsets.UTF_8)
            ChatMessage(role = role, content = content)
        }.toMutableList()
    }
}
