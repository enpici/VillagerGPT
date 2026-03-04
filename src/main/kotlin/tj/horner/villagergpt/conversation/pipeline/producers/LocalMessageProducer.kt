package tj.horner.villagergpt.conversation.pipeline.producers

import com.aallam.openai.api.BetaOpenAI
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bukkit.configuration.Configuration
import tj.horner.villagergpt.VillagerGPT
import tj.horner.villagergpt.conversation.VillagerConversation
import tj.horner.villagergpt.conversation.pipeline.ConversationMessageProducer
import tj.horner.villagergpt.observability.logContext
import kotlin.system.measureTimeMillis

@OptIn(BetaOpenAI::class)
class LocalMessageProducer(
    private val plugin: VillagerGPT,
    config: Configuration,
    private val requestSettings: ProviderRequestSettings
) : ConversationMessageProducer, AutoCloseable {
    private val client = HttpClient(Apache) {
        engine {
            socketTimeout = requestSettings.responseTimeoutMs
            connectTimeout = requestSettings.connectionTimeoutMs
            connectionRequestTimeout = requestSettings.connectionTimeoutMs
        }
    }
    private val endpoint = config.getString("local-model-url") ?: "http://localhost:8000/"
    private val sendJson = config.getBoolean("local-model-json", false)
    private val providerName = "local"

    override suspend fun produceNextMessage(conversation: VillagerConversation): String {
        return withExponentialRetry(
            provider = providerName,
            settings = requestSettings,
            metrics = plugin.providerMetrics,
            execute = {
                var responseBody = ""
                val duration = measureTimeMillis {
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
                        val payload = conversation.messages.joinToString("\n") { "${it.role}: ${it.content}" }
                        client.post(endpoint) { setBody(payload) }
                    }

                    responseBody = parseResponse(response)
                }

                plugin.providerMetrics.recordLatency(providerName, duration)
                val context = conversation.logContext(providerName)
                plugin.logger.log(
                    plugin.observabilitySettings.contextLogLevel,
                    "llm_request_completed ${context.asFields()} latencyMs=$duration"
                )
                responseBody
            },
            classifyError = { throwable ->
                if (throwable is LocalProviderHttpException) {
                    throwable.statusCode == 408 || throwable.statusCode == 429 || throwable.statusCode >= 500
                } else {
                    isTransientError(throwable)
                }
            }
        )
    }

    private suspend fun parseResponse(response: HttpResponse): String {
        val statusCode = response.status.value
        val body = response.bodyAsText()

        if (statusCode >= 400) {
            throw LocalProviderHttpException(statusCode, body)
        }

        return body
    }

    /**
     * Release HTTP resources used by this producer.
     */
    override fun close() {
        client.close()
    }
}

class LocalProviderHttpException(val statusCode: Int, message: String) : RuntimeException(message)
