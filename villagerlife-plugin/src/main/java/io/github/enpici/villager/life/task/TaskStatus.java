package io.github.enpici.villager.life.task;

public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    RETRYABLE_FAILED,
    FATAL_FAILED,
    CANCELLED,
    TIMEOUT
}
