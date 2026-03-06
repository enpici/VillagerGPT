package io.github.enpici.villager.life.task;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.village.VillageAI;

public interface Task {
    String id();

    TaskStatus status();

    long timeoutTicks();

    boolean canStart(Agent agent, VillageAI villageAI);

    void start(Agent agent, VillageAI villageAI);

    TaskStatus tick(Agent agent, VillageAI villageAI);

    void stop(Agent agent, VillageAI villageAI, TaskStatus reason);
}
