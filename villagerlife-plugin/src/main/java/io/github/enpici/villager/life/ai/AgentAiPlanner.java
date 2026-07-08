package io.github.enpici.villager.life.ai;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.agent.AgentGoal;
import io.github.enpici.villager.life.agent.NeedType;
import io.github.enpici.villager.life.role.AgentRole;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AgentAiPlanner {

    private static final Pattern CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.DOTALL);
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);
    private static final int MAX_REASON_LENGTH = 160;

    private final JavaPlugin plugin;
    private final HttpClient client;
    private final AgentSkillCatalog skillCatalog = new AgentSkillCatalog();

    public AgentAiPlanner(JavaPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutMs()))
                .build();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("ai-planning.enabled", true)
                && plugin.getConfig().getBoolean("ai-planning.agent-decisions.enabled", true);
    }

    public long cooldownTicks() {
        return Math.max(20L, plugin.getConfig().getLong("ai-planning.agent-decisions.cooldown-ticks", 600L));
    }

    public int minAcceptedPriority() {
        return clamp(plugin.getConfig().getInt("ai-planning.agent-decisions.min-accepted-priority", 30), 1, 100);
    }

    public int maxAcceptedPriority() {
        return clamp(plugin.getConfig().getInt("ai-planning.agent-decisions.max-accepted-priority", 88), 1, 100);
    }

    public int maxConcurrentRequests() {
        return Math.max(1, plugin.getConfig().getInt("ai-planning.agent-decisions.max-concurrent-requests", 1));
    }

    public CompletableFuture<AgentPlan> planGoal(Agent agent, VillageAI village, String deterministicGoal) {
        if (!enabled()) {
            return CompletableFuture.completedFuture(AgentPlan.rejected("disabled"));
        }

        String endpoint = plugin.getConfig().getString("ai-planning.local-model-url", "http://localhost:1234/v1/chat/completions");
        String model = plugin.getConfig().getString("ai-planning.model", "google/gemma-4-e4b");
        String body = requestBody(model, prompt(agent, village, deterministicGoal));
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(endpoint))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofMillis(timeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(AgentPlan.rejected("invalid_endpoint"));
        }

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(timeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return AgentPlan.rejected("http_" + response.statusCode());
                    }
                    return parseResponse(response.body());
                })
                .exceptionally(exception -> AgentPlan.rejected("ai_unavailable"));
    }

    AgentPlan parseResponse(String body) {
        String content = extractAssistantContent(body);
        Matcher jsonMatcher = JSON_OBJECT_PATTERN.matcher(content);
        String json = jsonMatcher.find() ? jsonMatcher.group() : content;

        String rawGoal = readString(json, "goal", "");
        AgentGoal goal = parseGoal(rawGoal);
        if (goal == null) {
            return AgentPlan.rejected("invalid_goal:" + clean(rawGoal));
        }

        int priority = clamp(readInt(json, "priority", 50), 1, maxAcceptedPriority());
        String reason = truncate(readString(json, "reason", "AI selected " + goal.name().toLowerCase(Locale.ROOT)), MAX_REASON_LENGTH);
        String skill = clean(readString(json, "skill", ""));
        AgentToolRequest tool = AgentToolRequest.parseFromPlanJson(json).orElse(null);
        return new AgentPlan(goal, priority, reason, skill, tool, true, "ok");
    }

    private String prompt(Agent agent, VillageAI village, String deterministicGoal) {
        return """
                You are the private planner of one Minecraft villager agent.
                Choose the next high-level goal for this individual. Return ONLY compact JSON:
                {"goal":"ONE_GOAL","priority":1-100,"skill":"skill_id","tool":{"name":"TOOL_NAME","material":"MATERIAL","amount":1,"target":"short target","reason":"short reason"},"reason":"short practical reason"}

                Allowed goals: %s.
                Available skills: %s.
                %s
                Hard rules:
                - Never invent inventory, food, blocks, beds, or completed work.
                - If hunger is critical and food is available, prefer SURVIVE_HUNGER.
                - If hunger is critical and no food exists, prefer WORK_FOOD, TRADE_GOODS, or GATHER_MATERIALS depending on role and village stock.
                - If monsters are a threat, prefer SEEK_SAFETY, PATROL, or BUILD_SHELTER.
                - Children should mostly choose GROW_UP, SOCIALIZE, REST, SURVIVE_HUNGER, or SEEK_SAFETY.
                - Prefer realistic player-like chains: perceive, gather raw material, craft missing tools/supplies, use the right tool, verify, recover.
                - Choose a skill from the list. If none fits, use look_around.
                - If the agent needs stone as a block, remember stone mined without silk touch becomes cobblestone and cobblestone must be smelted back to stone.
                - If a block can be broken by hand, hand is allowed, but tools improve speed and should be crafted when inputs exist.
                - Your decision is advisory; the server will validate and execute physically possible tasks.

                Agent: role=%s stage=%s needs=%s currentGoal=%s currentTask=%s lastEvent=%s deterministicGoal=%s.
                Village: population=%d target=%d beds=%d food=%d threat=%s pendingBlueprints=%s pendingMaterials=%s stock=%s reserved=%s.
                """.formatted(
                allowedGoals(),
                skillCatalog.promptSummary(agent.currentGoal()),
                clean(skillCatalog.toolSchema()),
                agent.role(),
                agent.lifeStage(),
                needs(agent),
                agent.currentGoal(),
                agent.activeTask() != null ? clean(agent.activeTask().id()) : "none",
                clean(agent.lastEvent()),
                clean(deterministicGoal),
                village.population(),
                village.populationTarget(),
                village.bedCount(),
                village.foodStock(),
                village.threatDetected(),
                clean(village.pendingBlueprintsSnapshot(5).toString()),
                clean(materials(village.pendingMaterials())),
                clean(materials(village.materialStockSnapshot())),
                clean(materials(village.reservedMaterialsSnapshot()))
        );
    }

    private String requestBody(String model, String prompt) {
        return """
                {"model":"%s","temperature":0.25,"messages":[{"role":"system","content":"You are a strict JSON planner for Minecraft agent goals."},{"role":"user","content":"%s"}]}
                """.formatted(escapeJson(model), escapeJson(prompt)).trim();
    }

    private String allowedGoals() {
        return Arrays.stream(AgentGoal.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    private String needs(Agent agent) {
        return Arrays.stream(NeedType.values())
                .map(type -> type.name().toLowerCase(Locale.ROOT) + "=" + Math.round(agent.needLevel(type)))
                .collect(Collectors.joining(","));
    }

    private String materials(Map<Material, Integer> materials) {
        if (materials == null || materials.isEmpty()) {
            return "{}";
        }
        return materials.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().name() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private AgentGoal parseGoal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AgentGoal.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String extractAssistantContent(String body) {
        Matcher matcher = CONTENT_PATTERN.matcher(body != null ? body : "");
        if (!matcher.find()) {
            return body != null ? body : "";
        }
        return unescapeJson(matcher.group(1));
    }

    private int readInt(String json, String key, int fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json != null ? json : "");
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private String readString(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json != null ? json : "");
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private int timeoutMs() {
        return Math.max(500, plugin.getConfig().getInt("ai-planning.request-timeout-ms", 5000));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String truncate(String value, int maxLength) {
        String clean = clean(value);
        if (clean.length() <= maxLength) {
            return clean;
        }
        return clean.substring(0, maxLength);
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').replace('"', '\'').trim();
    }

    private String escapeJson(String value) {
        return clean(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String unescapeJson(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\\", "\\");
    }

    public record AgentPlan(AgentGoal goal,
                            int priority,
                            String reason,
                            String skill,
                            AgentToolRequest tool,
                            boolean accepted,
                            String rejectionReason) {
        static AgentPlan rejected(String reason) {
            return new AgentPlan(null, 0, "", "", null, false, reason);
        }
    }
}
