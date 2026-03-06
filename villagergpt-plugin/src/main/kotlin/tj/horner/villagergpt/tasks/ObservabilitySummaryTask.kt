package tj.horner.villagergpt.tasks

import org.bukkit.scheduler.BukkitRunnable
import tj.horner.villagergpt.VillagerGPT

class ObservabilitySummaryTask(private val plugin: VillagerGPT) : BukkitRunnable() {
    override fun run() {
        val provider = plugin.providerName()
        val summary = plugin.providerMetrics.diagnosticsSummary(provider)
        plugin.logger.log(plugin.observabilitySettings.summaryLogLevel, "observability_summary $summary")
    }
}
