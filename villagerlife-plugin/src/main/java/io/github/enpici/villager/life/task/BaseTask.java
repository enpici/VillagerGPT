package io.github.enpici.villager.life.task;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.village.VillageAI;

public abstract class BaseTask implements Task {

    private final String id;
    private final long timeoutTicks;
    private long elapsedTicks;
    private TaskStatus status = TaskStatus.PENDING;

    protected BaseTask(String id, long timeoutTicks) {
        this.id = id;
        this.timeoutTicks = timeoutTicks;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public TaskStatus status() {
        return status;
    }

    @Override
    public long timeoutTicks() {
        return timeoutTicks;
    }

    @Override
    public void start(Agent agent, VillageAI villageAI) {
        this.status = TaskStatus.RUNNING;
        this.elapsedTicks = 0L;
        onStart(agent, villageAI);
    }

    @Override
    public TaskStatus tick(Agent agent, VillageAI villageAI) {
        if (status != TaskStatus.RUNNING) {
            return status;
        }

        elapsedTicks++;
        if (timeoutTicks > 0 && elapsedTicks > timeoutTicks) {
            status = TaskStatus.TIMEOUT;
            return status;
        }

        status = onTick(agent, villageAI);
        return status;
    }

    @Override
    public void stop(Agent agent, VillageAI villageAI, TaskStatus reason) {
        this.status = reason;
        onStop(agent, villageAI, reason);
    }

    protected void onStart(Agent agent, VillageAI villageAI) {
    }

    protected abstract TaskStatus onTick(Agent agent, VillageAI villageAI);

    protected void onStop(Agent agent, VillageAI villageAI, TaskStatus reason) {
    }
}
