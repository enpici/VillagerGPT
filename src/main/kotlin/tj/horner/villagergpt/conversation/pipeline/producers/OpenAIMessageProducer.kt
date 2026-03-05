package tj.horner.villagergpt.conversation.pipeline.producers

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import org.bukkit.configuration.Configuration
import tj.horner.villagergpt.VillagerGPT
import tj.horner.villagergpt.conversation.VillagerConversation
import tj.horner.villagergpt.conversation.pipeline.ConversationMessageProducer
import tj.horner.villagergpt.observability.logContext
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds

class OpenAIMessageProducer(
    private val plugin: VillagerGPT,
    config: Configuration,
    private val requestSettings: ProviderRequestSettings,
    apiKey: String
) : ConversationMessageProducer {
    private val openAI = OpenAI(
        OpenAIConfig(
            token = apiKey,
            logLevel = LogLevel.None,
            timeout = Timeout(
                request = requestSettings.responseTimeoutMs.milliseconds,
                connect = requestSettings.connectionTimeoutMs.milliseconds,
                socket = requestSettings.responseTimeoutMs.milliseconds
            )
        )
    )
    private val providerName = "openai"

    private val model = ModelId(config.getString("openai-model") ?: "gpt-3.5-turbo")

    @OptIn(BetaOpenAI::class)
    override suspend fun produceNextMessage(conversation: VillagerConversation): String {
        return withExponentialRetry(
            provider = providerName,
            settings = requestSettings,
            metrics = plugin.providerMetrics,
            execute = {
                var completionMessage = ""
                val duration = measureTimeMillis {
                    val request = ChatCompletionRequest(
                        model = model,
                        messages = conversation.messages,
                        temperature = 0.7,
                        user = conversation.player.uniqueId.toString()
                    )

                    val completion = openAI.chatCompletion(request)
                    completionMessage = completion.choices[0].message!!.content
                }

                plugin.providerMetrics.recordLatency(providerName, duration)
                val context = conversation.logContext(providerName)
                plugin.logger.log(
                    plugin.observabilitySettings.contextLogLevel,
                    "llm_request_completed ${context.asFields()} latencyMs=$duration"
                )
                completionMessage
            },
            classifyError = { throwable ->
                val message = throwable.message.orEmpty().lowercase()
                isTransientError(throwable) ||
                    message.contains("rate limit") ||
                    message.contains("too many requests") ||
                    message.contains("service unavailable")
            }
        )
    }
}
