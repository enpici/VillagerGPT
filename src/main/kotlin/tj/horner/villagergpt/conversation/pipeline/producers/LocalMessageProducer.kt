package tj.horner.villagergpt.conversation.pipeline.producers

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bukkit.configuration.Configuration
import tj.horner.villagergpt.VillagerGPT
import tj.horner.villagergpt.chat.ChatMessageTemplate
import tj.horner.villagergpt.conversation.VillagerConversation
import tj.horner.villagergpt.conversation.pipeline.ConversationMessageProducer
import com.aallam.openai.api.BetaOpenAI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import java.util.logging.Level

@OptIn(BetaOpenAI::class)
class LocalMessageProducer(
    private val plugin: VillagerGPT,
    config: Configuration
) : ConversationMessageProducer, AutoCloseable {
    private val client = HttpClient(Apache)
    private val endpoint = config.getString("local-model-url") ?: "http://localhost:8000/"
    private val sendJson = config.getBoolean("local-model-json", false)

    override suspend fun produceNextMessage(conversation: VillagerConversation): String {
        return try {
            val response = if (sendJson) {
                val payload = buildJsonObject {
                    put("messages", buildJsonArray {
                        conversation.messages.forEach {
                            add(buildJsonObject {
                                put("role", it.role.role)
                                put("content", it.content)
                            })
                        }
                    })
                }

                client.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(payload.toString())
                }
            } else {
                val payload = conversation.messages.joinToString("\n") { "${'$'}{it.role}: ${'$'}{it.content}" }
                client.post(endpoint) { setBody(payload) }
            }

            response.bodyAsText()
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to reach local model endpoint: ${'$'}{e.message}")
            val message = Component.text("Failed to contact local model. Please try again later.")
                .decorate(TextDecoration.ITALIC)
            conversation.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))
            throw e
        }
    }

    /**
     * Release HTTP resources used by this producer.
     */
    override fun close() {
        client.close()
    }
}
