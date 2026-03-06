package tj.horner.villagergpt.memory

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(BetaOpenAI::class)
class ConversationMemory(dbFile: String) {
    private val jdbcUrl = "jdbc:sqlite:$dbFile"
    private val writeExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    private val writeConnection: Connection = openConnection(readOnly = false)

    init {
        writeConnection.createStatement().use { stmt ->
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

            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS villagers (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    summary TEXT,
                    villager_role TEXT,
                    village_name TEXT,
                    relationships TEXT
                )
                """.trimIndent()
            )

            ensureColumn(stmt, "villagers", "villager_role", "TEXT")
            ensureColumn(stmt, "villagers", "village_name", "TEXT")
            ensureColumn(stmt, "villagers", "relationships", "TEXT")
        }

        writeConnection.createStatement().use { stmt ->
            stmt.execute("PRAGMA journal_mode=WAL")
            stmt.execute("PRAGMA busy_timeout=5000")
        }
    }

    suspend fun loadMessages(uuid: UUID, limit: Int): List<ChatMessage> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<Pair<String, String>>()
        withReadConnection { connection ->
            connection.prepareStatement("SELECT role, content FROM messages WHERE villager_uuid=? ORDER BY id DESC LIMIT ?").use { ps ->
                ps.setString(1, uuid.toString())
                ps.setInt(2, limit)
                val rs = ps.executeQuery()
                while (rs.next()) {
                    rows.add(rs.getString("role") to rs.getString("content"))
                }
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
        withWriteConnection { connection ->
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
    }

    fun loadGossip(uuid: UUID, limit: Int): List<String> {
        val gossip = mutableListOf<String>()
        withReadConnection { connection ->
            connection.prepareStatement("SELECT content FROM gossip WHERE villager_uuid=? ORDER BY id DESC LIMIT ?").use { ps ->
                ps.setString(1, uuid.toString())
                ps.setInt(2, limit)
                val rs = ps.executeQuery()
                while (rs.next()) {
                    gossip.add(rs.getString("content"))
                }
            }
        }
        return gossip.reversed()
    }

    fun addGossip(uuid: UUID, entries: List<String>, maxEntries: Int) {
        if (entries.isEmpty()) return
        withWriteConnection { connection ->
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
    }

    data class VillagerInfo(
        val name: String,
        val summary: String?,
        val villagerRole: String?,
        val villageName: String?,
        val relationships: String?
    )

    fun getVillagerInfo(uuid: UUID): VillagerInfo? {
        return withReadConnection { connection ->
            connection.prepareStatement("SELECT name, summary, villager_role, village_name, relationships FROM villagers WHERE uuid=?").use { ps ->
                ps.setString(1, uuid.toString())
                val rs = ps.executeQuery()
                if (rs.next()) {
                    VillagerInfo(
                        name = rs.getString("name"),
                        summary = rs.getString("summary"),
                        villagerRole = rs.getString("villager_role"),
                        villageName = rs.getString("village_name"),
                        relationships = rs.getString("relationships")
                    )
                } else {
                    null
                }
            }
        }
    }

    fun insertVillager(uuid: UUID, name: String) {
        withWriteConnection { connection ->
            connection.prepareStatement("INSERT INTO villagers (uuid, name) VALUES (?, ?)").use { ps ->
                ps.setString(1, uuid.toString())
                ps.setString(2, name)
                ps.executeUpdate()
            }
        }
    }

    fun updateVillagerSummary(uuid: UUID, summary: String) {
        withWriteConnection { connection ->
            connection.prepareStatement("UPDATE villagers SET summary=? WHERE uuid=?").use { ps ->
                ps.setString(1, summary)
                ps.setString(2, uuid.toString())
                ps.executeUpdate()
            }
        }
    }

    fun updateVillagerMetadata(uuid: UUID, role: String?, villageName: String?, relationships: String?) {
        withWriteConnection { connection ->
            connection.prepareStatement("UPDATE villagers SET villager_role=?, village_name=?, relationships=? WHERE uuid=?").use { ps ->
                ps.setString(1, role)
                ps.setString(2, villageName)
                ps.setString(3, relationships)
                ps.setString(4, uuid.toString())
                ps.executeUpdate()
            }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return

        writeExecutor.shutdown()
        writeExecutor.awaitTermination(5, TimeUnit.SECONDS)
        writeConnection.close()
    }

    private fun <T> withReadConnection(block: (Connection) -> T): T {
        check(!closed.get()) { "ConversationMemory is closed" }
        openConnection(readOnly = true).use { connection ->
            return block(connection)
        }
    }

    private fun <T> withWriteConnection(block: (Connection) -> T): T {
        check(!closed.get()) { "ConversationMemory is closed" }
        return writeExecutor.submit(Callable {
            withRetryOnLock {
                withTransaction(writeConnection) {
                    block(writeConnection)
                }
            }
        }).get()
    }

    private fun <T> withTransaction(connection: Connection, block: () -> T): T {
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            val result = block()
            connection.commit()
            return result
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    private fun <T> withRetryOnLock(maxRetries: Int = 4, block: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: SQLException) {
                val isLocked = e.message?.contains("database is locked", ignoreCase = true) == true
                if (!isLocked || attempt >= maxRetries) throw e
                Thread.sleep((50L * (attempt + 1)).coerceAtMost(500L))
                attempt++
            }
        }
    }

    private fun openConnection(readOnly: Boolean): Connection {
        val connection = DriverManager.getConnection(jdbcUrl)
        connection.createStatement().use { stmt ->
            stmt.execute("PRAGMA busy_timeout=5000")
            if (readOnly) {
                stmt.execute("PRAGMA query_only=ON")
            }
        }
        return connection
    }

    private fun ensureColumn(statement: java.sql.Statement, table: String, column: String, type: String) {
        val hasColumn = statement.executeQuery("PRAGMA table_info($table)").use { rs ->
            var found = false
            while (rs.next()) {
                if (rs.getString("name").equals(column, ignoreCase = true)) {
                    found = true
                    break
                }
            }
            found
        }

        if (!hasColumn) {
            statement.executeUpdate("ALTER TABLE $table ADD COLUMN $column $type")
        }
    }
}
