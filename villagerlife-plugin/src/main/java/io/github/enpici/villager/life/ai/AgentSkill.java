package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.AgentGoal;

import java.util.List;

public record AgentSkill(
        String id,
        int level,
        AgentGoal suggestedGoal,
        String summary,
        List<String> preconditions,
        List<String> steps,
        List<String> success,
        List<String> recovery,
        List<AgentTool> tools
) {

    public String compactPromptLine() {
        return id + "(level=" + level
                + ", goal=" + suggestedGoal
                + ", tools=" + tools
                + ", use_when=" + summary + ")";
    }
}
