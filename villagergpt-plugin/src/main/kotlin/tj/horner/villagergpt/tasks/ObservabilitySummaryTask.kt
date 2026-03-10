package io.github.enpici.villager.gpt.tasks

import org.bukkit.scheduler.BukkitRunnable
import io.github.enpici.villager.gpt.VillagerGPT

class ObservabilitySummaryTask(private val plugin: VillagerGPT) : BukkitRunnable() {
    override fun run() {
        val provider = plugin.providerName()
        val summary = plugin.providerMetrics.diagnosticsSummary(provider)
        plugin.logger.log(plugin.observabilitySettings.summaryLogLevel, "observability_summary $summary")
    }
}
