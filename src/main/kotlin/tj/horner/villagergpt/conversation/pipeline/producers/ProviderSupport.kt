package tj.horner.villagergpt.conversation.pipeline.producers

import kotlinx.coroutines.delay
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
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

    fun snapshot(provider: String): String {
        val totalRequests = requestCounts[provider]?.sum() ?: 0
        val totalLatency = latencyTotals[provider]?.sum() ?: 0
        val averageLatency = if (totalRequests == 0L) 0 else totalLatency / totalRequests
        val retries = retryCounts[provider]?.sum() ?: 0
        val recoverableErrors = recoverableErrorCounts[provider]?.sum() ?: 0
        val nonRecoverableErrors = nonRecoverableErrorCounts[provider]?.sum() ?: 0

        return "provider=$provider avgLatencyMs=$averageLatency retries=$retries recoverableErrors=$recoverableErrors nonRecoverableErrors=$nonRecoverableErrors"
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
