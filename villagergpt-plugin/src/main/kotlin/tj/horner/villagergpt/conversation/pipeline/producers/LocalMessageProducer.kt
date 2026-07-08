package io.github.enpici.villager.gpt.conversation.pipeline.producers

import com.aallam.openai.api.BetaOpenAI
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.bukkit.configuration.Configuration
import io.github.enpici.villager.gpt.VillagerGPT
import io.github.enpici.villager.gpt.conversation.VillagerConversation
import io.github.enpici.villager.gpt.conversation.pipeline.ConversationMessageProducer
import io.github.enpici.villager.gpt.observability.logContext
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
    private val openAiCompatible = config.getBoolean("local-model-openai-compatible", false)
    private val model = config.getString("local-model-name") ?: "local-model"
    private val temperature = config.getDouble("local-model-temperature", 0.7)
    private val providerName = "local"

    override suspend fun produceNextMessage(conversation: VillagerConversation): String {
        return withExponentialRetry(
            provider = providerName,
            settings = requestSettings,
            metrics = plugin.providerMetrics,
            execute = {
                var responseBody = ""
                val duration = measureTimeMillis {
                    val response = if (openAiCompatible) {
                        val payload = buildJsonObject {
                            put("model", model)
                            put("temperature", temperature)
                            put("stream", false)
                            put("messages", conversationMessagesPayload(conversation))
                        }

                        client.post(endpoint) {
                            contentType(ContentType.Application.Json)
                            setBody(payload.toString())
                        }
                    } else if (sendJson) {
                        val payload = buildJsonObject {
                            put("messages", conversationMessagesPayload(conversation))
                        }

                        client.post(endpoint) {
                            contentType(ContentType.Application.Json)
                            setBody(payload.toString())
                        }
                    } else {
                        val payload = conversation.messages.joinToString("\n") { "${it.role}: ${it.content}" }
                        client.post(endpoint) { setBody(payload) }
                    }

                    responseBody = parseResponse(response, openAiCompatible)
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

    private fun conversationMessagesPayload(conversation: VillagerConversation): JsonElement {
        return buildJsonArray {
            conversation.messages.forEach {
                add(buildJsonObject {
                    put("role", it.role.role)
                    put("content", it.content)
                })
            }
        }
    }

    private suspend fun parseResponse(response: HttpResponse, openAiCompatible: Boolean): String {
        val statusCode = response.status.value
        val body = response.bodyAsText()

        if (statusCode >= 400) {
            throw LocalProviderHttpException(statusCode, body)
        }

        return if (openAiCompatible) {
            LocalModelResponseParser.openAiChatContent(body)
        } else {
            body
        }
    }

    /**
     * Release HTTP resources used by this producer.
     */
    override fun close() {
        client.close()
    }
}

class LocalProviderHttpException(val statusCode: Int, message: String) : RuntimeException(message)

internal object LocalModelResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun openAiChatContent(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        val firstChoice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw LocalProviderHttpException(200, "Local model response did not include choices")

        val content = firstChoice["message"]
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: firstChoice["text"]?.jsonPrimitive?.contentOrNull

        return content?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw LocalProviderHttpException(200, "Local model response did not include message content")
    }
}
