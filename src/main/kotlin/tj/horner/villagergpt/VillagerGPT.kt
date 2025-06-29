package tj.horner.villagergpt

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.bukkit.registerSuspendingEvents
import com.github.shynixn.mccoroutine.bukkit.setSuspendingExecutor
import tj.horner.villagergpt.commands.ClearCommand
import tj.horner.villagergpt.commands.EndCommand
import tj.horner.villagergpt.commands.TalkCommand
import tj.horner.villagergpt.conversation.VillagerConversationManager
import tj.horner.villagergpt.memory.ConversationMemory
import tj.horner.villagergpt.conversation.pipeline.MessageProcessorPipeline
import tj.horner.villagergpt.conversation.pipeline.processors.ActionProcessor
import tj.horner.villagergpt.conversation.pipeline.processors.TradeOfferProcessor
import tj.horner.villagergpt.conversation.pipeline.producers.OpenAIMessageProducer
import tj.horner.villagergpt.conversation.pipeline.producers.LocalMessageProducer
import tj.horner.villagergpt.handlers.ConversationEventsHandler
import tj.horner.villagergpt.tasks.EndStaleConversationsTask
import tj.horner.villagergpt.tasks.EnvironmentWatcher
import tj.horner.villagergpt.tasks.GossipManager

import java.util.logging.Level

class VillagerGPT : SuspendingJavaPlugin() {
    lateinit var memory: ConversationMemory
        private set

    val conversationManager = VillagerConversationManager(this)
    val messagePipeline = MessageProcessorPipeline(
        createMessageProducer(),
        listOf(
            ActionProcessor(),
            TradeOfferProcessor(logger)
        )
    )

    override suspend fun onEnableAsync() {
        saveDefaultConfig()

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

        memory.close()

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
    }

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
        "local" -> LocalMessageProducer(this, config)
        else -> OpenAIMessageProducer(config)
    }
}
