package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.task.BaseTask;
import io.github.enpici.villager.life.task.TaskStatus;
import io.github.enpici.villager.life.village.VillageAI;

public class BuildStructureTask extends BaseTask {

    private final String blueprintId;

    public BuildStructureTask(String blueprintId) {
        super("build_structure", 200L);
        this.blueprintId = blueprintId;
    }

    @Override
    public boolean canStart(Agent agent, VillageAI villageAI) {
        return villageAI.blueprintService().hasBlueprint(blueprintId);
    }

    @Override
    protected TaskStatus onTick(Agent agent, VillageAI villageAI) {
        boolean built = villageAI.blueprintService().placeStructure(blueprintId, villageAI, agent, villageAI.center());
        return built ? TaskStatus.SUCCESS : TaskStatus.FAILED;
    }
}
