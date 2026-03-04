package tj.horner.villagergpt.conversation.pipeline.producers

import kotlinx.coroutines.delay
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder
import kotlin.math.min

class ProviderException(
    val provider: String,
    val recoverable: Boolean,
    val fallbackMessage: String,
    cause: Throwable
) : RuntimeException("$provider provider request failed", cause)

data class RetrySettings(
    val maxAttempts: Int,
    val baseDelayMs: Long,
    val maxDelayMs: Long
)

data class ProviderRequestSettings(
    val connectionTimeoutMs: Int,
    val responseTimeoutMs: Int,
    val retrySettings: RetrySettings,
    val fallbackMessage: String
)

class ProviderMetricsRegistry {
    private val latencyTotals = ConcurrentHashMap<String, LongAdder>()
    private val requestCounts = ConcurrentHashMap<String, LongAdder>()
    private val retryCounts = ConcurrentHashMap<String, LongAdder>()
    private val recoverableErrorCounts = ConcurrentHashMap<String, LongAdder>()
    private val nonRecoverableErrorCounts = ConcurrentHashMap<String, LongAdder>()
    private val activeConversations = AtomicInteger(0)
    private val sessionMessageCounts = ConcurrentHashMap<String, LongAdder>()
    private val sessionMessageChars = ConcurrentHashMap<String, LongAdder>()
    private val completedSessionCount = LongAdder()
    private val completedSessionMessages = LongAdder()
    private val completedSessionChars = LongAdder()

    fun recordLatency(provider: String, latencyMs: Long) {
        latencyTotals.computeIfAbsent(provider) { LongAdder() }.add(latencyMs)
        requestCounts.computeIfAbsent(provider) { LongAdder() }.increment()
    }

    fun recordRetry(provider: String) {
        retryCounts.computeIfAbsent(provider) { LongAdder() }.increment()
    }

    fun recordError(provider: String, recoverable: Boolean) {
        if (recoverable) {
            recoverableErrorCounts.computeIfAbsent(provider) { LongAdder() }.increment()
        } else {
            nonRecoverableErrorCounts.computeIfAbsent(provider) { LongAdder() }.increment()
        }
    }

    fun recordConversationStarted(conversationId: String) {
        activeConversations.incrementAndGet()
        sessionMessageCounts.putIfAbsent(conversationId, LongAdder())
        sessionMessageChars.putIfAbsent(conversationId, LongAdder())
    }

    fun recordConversationEnded(conversationId: String) {
        activeConversations.updateAndGet { current -> if (current > 0) current - 1 else 0 }
        val messages = sessionMessageCounts.remove(conversationId)?.sum() ?: 0
        val chars = sessionMessageChars.remove(conversationId)?.sum() ?: 0
        completedSessionCount.increment()
        completedSessionMessages.add(messages)
        completedSessionChars.add(chars)
    }

    fun recordConversationMessage(conversationId: String, messageLength: Int) {
        sessionMessageCounts.computeIfAbsent(conversationId) { LongAdder() }.increment()
        sessionMessageChars.computeIfAbsent(conversationId) { LongAdder() }.add(messageLength.toLong())
    }

    fun snapshot(provider: String): String {
        val totalRequests = requestCounts[provider]?.sum() ?: 0
        val totalLatency = latencyTotals[provider]?.sum() ?: 0
        val averageLatency = if (totalRequests == 0L) 0 else totalLatency / totalRequests
        val retries = retryCounts[provider]?.sum() ?: 0
        val recoverableErrors = recoverableErrorCounts[provider]?.sum() ?: 0
        val nonRecoverableErrors = nonRecoverableErrorCounts[provider]?.sum() ?: 0
        val totalErrors = recoverableErrors + nonRecoverableErrors
        val errorRatePct = if (totalRequests == 0L) 0.0 else (totalErrors.toDouble() / totalRequests.toDouble()) * 100

        return "provider=$provider avgLatencyMs=$averageLatency requests=$totalRequests retries=$retries recoverableErrors=$recoverableErrors nonRecoverableErrors=$nonRecoverableErrors errorRatePct=%.2f".format(errorRatePct)
    }

    fun diagnosticsSummary(provider: String): String {
        val activeSessionMessages = sessionMessageCounts.values.sumOf { it.sum() }
        val activeSessionChars = sessionMessageChars.values.sumOf { it.sum() }
        val completedSessions = completedSessionCount.sum()
        val completedMessages = completedSessionMessages.sum()
        val completedChars = completedSessionChars.sum()

        val allMessages = activeSessionMessages + completedMessages
        val allChars = activeSessionChars + completedChars
        val totalSessions = activeConversations.get().toLong() + completedSessions
        val avgMessageSize = if (allMessages == 0L) 0 else allChars / allMessages
        val avgMessagesPerSession = if (totalSessions == 0L) 0 else allMessages / totalSessions

        return "${snapshot(provider)} activeConversations=${activeConversations.get()} totalMessages=$allMessages avgMessageSizeChars=$avgMessageSize avgMessagesPerSession=$avgMessagesPerSession"
    }
}

suspend fun <T> withExponentialRetry(
    provider: String,
    settings: ProviderRequestSettings,
    metrics: ProviderMetricsRegistry,
    execute: suspend () -> T,
    classifyError: (Throwable) -> Boolean
): T {
    var attempt = 0
    var delayMs = settings.retrySettings.baseDelayMs

    while (true) {
        attempt += 1
        try {
            return execute()
        } catch (e: Throwable) {
            val recoverable = classifyError(e)
            metrics.recordError(provider, recoverable)

            val shouldRetry = recoverable && attempt < settings.retrySettings.maxAttempts
            if (!shouldRetry) {
                throw ProviderException(provider, recoverable, settings.fallbackMessage, e)
            }

            metrics.recordRetry(provider)
            delay(delayMs)
            delayMs = min(delayMs * 2, settings.retrySettings.maxDelayMs)
        }
    }
}

fun isTransientError(exception: Throwable): Boolean {
    val root = generateSequence(exception) { it.cause }.last()
    return root is SocketTimeoutException ||
        root is ConnectException ||
        root is UnknownHostException ||
        root.message?.contains("timeout", ignoreCase = true) == true ||
        root.message?.contains("temporarily", ignoreCase = true) == true
}
