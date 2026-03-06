package tj.horner.villagergpt.config

import org.bukkit.configuration.Configuration

object SecretsResolver {
    fun resolveOpenAiKey(config: Configuration): String? {
        val envVarName = config.getString("openai-key-env-var")?.trim().orEmpty().ifEmpty { "OPENAI_API_KEY" }
        val keyFromEnv = System.getenv(envVarName)?.trim().orEmpty()
        if (keyFromEnv.isNotEmpty()) return keyFromEnv

        val keyFromLegacyConfig = config.getString("openai-key")?.trim().orEmpty()
        return keyFromLegacyConfig.ifEmpty { null }
    }
}

