package tj.horner.villagergpt.conversation.pipeline.producers

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import org.bukkit.configuration.Configuration
import tj.horner.villagergpt.conversation.VillagerConversation
import tj.horner.villagergpt.conversation.pipeline.ConversationMessageProducer

class LocalMessageProducer(config: Configuration) : ConversationMessageProducer {
    private val client = HttpClient(Apache)
    private val endpoint = config.getString("local-model-url") ?: "http://localhost:8000/"

    override suspend fun produceNextMessage(conversation: VillagerConversation): String {
        val payload = conversation.messages.joinToString("\n") { "${'$'}{it.role}: ${'$'}{it.content}" }
        val response = client.post(endpoint) { setBody(payload) }
        return response.bodyAsText()
    }
}
