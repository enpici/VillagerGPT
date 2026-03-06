package io.github.enpici.villager.life.blueprint;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class BuildTelemetry {

    private static final int MAX_RECENT_ERRORS = 10;

    private final JavaPlugin plugin;
    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();
    private final AtomicLong retryCount = new AtomicLong();
    private final Deque<BuildFailureRecord> recentFailures = new LinkedList<>();

    public BuildTelemetry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void logBuildStarted(UUID villageId, UUID agentUuid, String blueprintId) {
        log("build_started", villageId, agentUuid, blueprintId, null, null, null);
    }

    public void logBuildStep(UUID villageId, UUID agentUuid, String blueprintId, Integer stepIndex, Long elapsedMs) {
        log("build_step", villageId, agentUuid, blueprintId, stepIndex, elapsedMs, null);
    }

    public void logBuildFailed(UUID villageId, UUID agentUuid, String blueprintId, Integer stepIndex, Long elapsedMs, String reason) {
        failedCount.incrementAndGet();
        log("build_failed", villageId, agentUuid, blueprintId, stepIndex, elapsedMs, reason);
        addFailure(villageId, agentUuid, blueprintId, stepIndex, elapsedMs, reason);
    }

    public void logBuildCompleted(UUID villageId, UUID agentUuid, String blueprintId, Integer stepIndex, Long elapsedMs) {
        successCount.incrementAndGet();
        log("build_completed", villageId, agentUuid, blueprintId, stepIndex, elapsedMs, null);
    }

    public void incrementRetry() {
        retryCount.incrementAndGet();
    }

    public BuildCounters snapshotCounters() {
        return new BuildCounters(successCount.get(), failedCount.get(), retryCount.get());
    }

    public synchronized List<BuildFailureRecord> recentFailuresSnapshot(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<BuildFailureRecord> snapshot = new ArrayList<>(Math.min(limit, recentFailures.size()));
        int index = 0;
        for (BuildFailureRecord failure : recentFailures) {
            if (index++ >= limit) {
                break;
            }
            snapshot.add(failure);
        }
        return snapshot;
    }

    public synchronized void resetCountersAndErrors() {
        successCount.set(0);
        failedCount.set(0);
        retryCount.set(0);
        recentFailures.clear();
    }

    private synchronized void addFailure(UUID villageId, UUID agentUuid, String blueprintId, Integer stepIndex, Long elapsedMs, String reason) {
        recentFailures.addFirst(new BuildFailureRecord(System.currentTimeMillis(), villageId, agentUuid, blueprintId, stepIndex, elapsedMs, reason));
        while (recentFailures.size() > MAX_RECENT_ERRORS) {
            recentFailures.removeLast();
        }
    }

    private void log(String event, UUID villageId, UUID agentUuid, String blueprintId, Integer stepIndex, Long elapsedMs, String reason) {
        String payload = String.format(Locale.ROOT,
                "event=%s villageId=%s agentUuid=%s blueprintId=%s stepIndex=%s elapsedMs=%s reason=%s",
                event,
                asString(villageId),
                asString(agentUuid),
                asString(blueprintId),
                asString(stepIndex),
                asString(elapsedMs),
                asString(reason));
        plugin.getLogger().info(payload);
    }

    private String asString(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    public record BuildCounters(long successful, long failed, long retries) {
    }

    public record BuildFailureRecord(long timestampMs,
                                     UUID villageId,
                                     UUID agentUuid,
                                     String blueprintId,
                                     Integer stepIndex,
                                     Long elapsedMs,
                                     String reason) {
    }
}
