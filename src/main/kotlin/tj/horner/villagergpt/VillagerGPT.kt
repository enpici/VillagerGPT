package tj.horner.villagergpt

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.bukkit.registerSuspendingEvents
import com.github.shynixn.mccoroutine.bukkit.setSuspendingExecutor
import tj.horner.villagergpt.commands.ClearCommand
import tj.horner.villagergpt.commands.EndCommand
import tj.horner.villagergpt.commands.TalkCommand
import tj.horner.villagergpt.conversation.VillagerConversationManager
import tj.horner.villagergpt.memory.ConversationMemory
import org.bukkit.NamespacedKey
import tj.horner.villagergpt.PersistentDataKeys
import tj.horner.villagergpt.conversation.pipeline.MessageProcessorPipeline
import tj.horner.villagergpt.conversation.pipeline.processors.ActionProcessor
import tj.horner.villagergpt.conversation.pipeline.processors.TradeOfferProcessor
import tj.horner.villagergpt.conversation.pipeline.producers.OpenAIMessageProducer
import tj.horner.villagergpt.conversation.pipeline.producers.LocalMessageProducer
import tj.horner.villagergpt.conversation.pipeline.producers.ProviderMetricsRegistry
import tj.horner.villagergpt.conversation.pipeline.producers.ProviderRequestSettings
import tj.horner.villagergpt.conversation.pipeline.producers.RetrySettings
import tj.horner.villagergpt.handlers.ConversationEventsHandler
import tj.horner.villagergpt.observability.ObservabilitySettings
import tj.horner.villagergpt.observability.readObservabilitySettings
import tj.horner.villagergpt.tasks.EndStaleConversationsTask
import tj.horner.villagergpt.tasks.EnvironmentWatcher
import tj.horner.villagergpt.tasks.GossipManager
import tj.horner.villagergpt.tasks.ObservabilitySummaryTask

import java.util.logging.Level

class VillagerGPT : SuspendingJavaPlugin() {
    lateinit var memory: ConversationMemory
        private set

    val conversationManager = VillagerConversationManager(this)
    val providerMetrics = ProviderMetricsRegistry()
    lateinit var observabilitySettings: ObservabilitySettings
        private set
    private val messageProducer = createMessageProducer()
    val messagePipeline = MessageProcessorPipeline(
        messageProducer,
        listOf(
            ActionProcessor(),
            TradeOfferProcessor(logger)
        )
    )

    override suspend fun onEnableAsync() {
        saveDefaultConfig()
        observabilitySettings = readObservabilitySettings(config)
        logger.level = observabilitySettings.rootLogLevel

        PersistentDataKeys.PERSONALITY = NamespacedKey(this, "personality")

        val dbPath = config.getString("memory.db-path") ?: "memory.db"
        val dbFile = if (java.io.File(dbPath).isAbsolute) java.io.File(dbPath) else java.io.File(dataFolder, dbPath)
        dbFile.parentFile.mkdirs()
        memory = ConversationMemory(dbFile.path)


        if (!validateConfig()) {
            logger.log(Level.WARNING, "VillagerGPT has not been configured correctly! Please check your configuration values.")
            return
        }

        setCommandExecutors()
        registerEvents()
        scheduleTasks()
    }

    override fun onDisable() {
        logger.info("Ending all conversations")
        conversationManager.endAllConversations()

        if (messageProducer is AutoCloseable) {
            runCatching { (messageProducer as AutoCloseable).close() }
                .onFailure { logger.log(Level.WARNING, "Failed to close message producer", it) }
        }

        if (::memory.isInitialized) {
            runCatching { memory.close() }
                .onFailure { logger.log(Level.WARNING, "Failed to close conversation memory", it) }
        }
    }

    private fun setCommandExecutors() {
        getCommand("ttv")!!.setSuspendingExecutor(TalkCommand(this))
        getCommand("ttvclear")!!.setSuspendingExecutor(ClearCommand(this))
        getCommand("ttvend")!!.setSuspendingExecutor(EndCommand(this))
    }

    private fun registerEvents() {
        server.pluginManager.registerSuspendingEvents(ConversationEventsHandler(this), this)
    }

    private fun scheduleTasks() {
        EndStaleConversationsTask(this).runTaskTimer(this, 0L, 200L)
        val envInterval = config.getLong("environment.interval", 20L)
        EnvironmentWatcher(this).runTaskTimer(this, 0L, envInterval)
        GossipManager(this).runTaskTimer(this, 0L, 200L)
        ObservabilitySummaryTask(this).runTaskTimer(this, 0L, observabilitySettings.summaryIntervalTicks)
    }

    fun providerName(): String = (config.getString("provider") ?: "openai").lowercase()

    private fun validateConfig(): Boolean {
        val provider = config.getString("provider") ?: "openai"
        return if (provider.equals("local", ignoreCase = true)) {
            val url = config.getString("local-model-url") ?: return false
            url.trim().isNotEmpty()
        } else {
            val openAiKey = config.getString("openai-key") ?: return false
            openAiKey.trim().isNotEmpty()
        }
    }

    private fun createMessageProducer() = when (config.getString("provider")?.lowercase()) {
        "local" -> LocalMessageProducer(this, config, readProviderRequestSettings())
        else -> OpenAIMessageProducer(this, config, readProviderRequestSettings())
    }

    private fun readProviderRequestSettings(): ProviderRequestSettings {
        val section = config.getConfigurationSection("provider-settings")
        val retry = section?.getConfigurationSection("retry")

        return ProviderRequestSettings(
            connectionTimeoutMs = section?.getInt("connection-timeout-ms") ?: 5000,
            responseTimeoutMs = section?.getInt("response-timeout-ms") ?: 30000,
            retrySettings = RetrySettings(
                maxAttempts = retry?.getInt("max-attempts") ?: 3,
                baseDelayMs = retry?.getLong("base-delay-ms") ?: 500,
                maxDelayMs = retry?.getLong("max-delay-ms") ?: 4000
            ),
            fallbackMessage = section?.getString("fallback-message")
                ?: "No pude responder ahora mismo. Inténtalo de nuevo en unos segundos."
        )
    }
}
