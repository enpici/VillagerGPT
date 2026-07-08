package io.github.enpici.villager.life.planning;

import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiShelterPlanner {

    private static final Pattern CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.DOTALL);
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);
    private static final Set<Material> SAFE_WALL_MATERIALS = Set.of(
            Material.OAK_PLANKS,
            Material.SPRUCE_PLANKS,
            Material.BIRCH_PLANKS,
            Material.COBBLESTONE,
            Material.STONE,
            Material.DIRT
    );

    private final JavaPlugin plugin;
    private final HttpClient client;

    public AiShelterPlanner(JavaPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutMs()))
                .build();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("ai-planning.enabled", true)
                && plugin.getConfig().getBoolean("ai-planning.shelter.enabled", true);
    }

    public CompletableFuture<ShelterPlan> planShelter(VillageAI village, String trigger) {
        if (!enabled()) {
            return CompletableFuture.completedFuture(fallback("disabled"));
        }

        String endpoint = plugin.getConfig().getString("ai-planning.local-model-url", "http://localhost:1234/v1/chat/completions");
        String model = plugin.getConfig().getString("ai-planning.model", "google/gemma-4-e4b");
        String body = requestBody(model, prompt(village, trigger));
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(endpoint))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofMillis(timeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(fallback("invalid_endpoint"));
        }

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(timeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return fallback("http_" + response.statusCode());
                    }
                    return parseResponse(response.body());
                })
                .exceptionally(exception -> fallback("ai_unavailable"));
    }

    private ShelterPlan parseResponse(String body) {
        String content = extractAssistantContent(body);
        Matcher jsonMatcher = JSON_OBJECT_PATTERN.matcher(content);
        String json = jsonMatcher.find() ? jsonMatcher.group() : content;

        int width = clamp(readInt(json, "width", fallbackWidth()), 3, 9);
        int height = clamp(readInt(json, "height", fallbackHeight()), 3, 6);
        int depth = clamp(readInt(json, "depth", fallbackDepth()), 3, 9);
        if (width % 2 == 0) width++;
        if (depth % 2 == 0) depth++;

        Material wall = safeMaterial(readString(json, "wallMaterial", fallbackWallMaterial().name()), fallbackWallMaterial());
        Material roof = safeMaterial(readString(json, "roofMaterial", wall.name()), wall);
        boolean door = readBoolean(json, "includeDoor", true);
        boolean torches = readBoolean(json, "includeTorches", false);
        String reason = readString(json, "reason", "AI shelter plan");

        return new ShelterPlan(width, height, depth, wall, roof, door, torches, "ai", reason);
    }

    private ShelterPlan fallback(String reason) {
        ShelterPlan base = ShelterPlan.fallback();
        return new ShelterPlan(
                fallbackWidth(),
                fallbackHeight(),
                fallbackDepth(),
                fallbackWallMaterial(),
                fallbackRoofMaterial(),
                base.includeDoor(),
                base.includeTorches(),
                "fallback",
                reason
        );
    }

    private String prompt(VillageAI village, String trigger) {
        return """
                You design tiny emergency Minecraft villager shelters.
                Return ONLY compact JSON with keys:
                width, height, depth, wallMaterial, roofMaterial, includeDoor, includeTorches, reason.
                Constraints: width/depth odd 3-9, height 3-6.
                Materials allowed: OAK_PLANKS, SPRUCE_PLANKS, BIRCH_PLANKS, COBBLESTONE, STONE, DIRT.
                Prefer cheap materials already in stock. Build must be 4 walls and a roof for night monster safety.
                Context: trigger=%s population=%d food=%d beds=%d stock=%s threat=%s.
                """.formatted(
                clean(trigger),
                village.population(),
                village.foodStock(),
                village.bedCount(),
                clean(village.materialStockSnapshot().toString()),
                village.threatDetected()
        );
    }

    private String requestBody(String model, String prompt) {
        return """
                {"model":"%s","temperature":0.2,"messages":[{"role":"system","content":"You are a strict JSON planner for Minecraft construction."},{"role":"user","content":"%s"}]}
                """.formatted(escapeJson(model), escapeJson(prompt)).trim();
    }

    private String extractAssistantContent(String body) {
        Matcher matcher = CONTENT_PATTERN.matcher(body != null ? body : "");
        if (!matcher.find()) {
            return body != null ? body : "";
        }
        return unescapeJson(matcher.group(1));
    }

    private int readInt(String json, String key, int fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private boolean readBoolean(String json, String key, boolean fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE).matcher(json);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
    }

    private String readString(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private Material safeMaterial(String raw, Material fallback) {
        Material material = Material.matchMaterial(raw != null ? raw.toUpperCase(Locale.ROOT) : "");
        return material != null && SAFE_WALL_MATERIALS.contains(material) ? material : fallback;
    }

    private int timeoutMs() {
        return Math.max(500, plugin.getConfig().getInt("ai-planning.request-timeout-ms", 5000));
    }

    private int fallbackWidth() {
        return odd(clamp(plugin.getConfig().getInt("ai-planning.shelter.fallback-width", 5), 3, 9));
    }

    private int fallbackHeight() {
        return clamp(plugin.getConfig().getInt("ai-planning.shelter.fallback-height", 4), 3, 6);
    }

    private int fallbackDepth() {
        return odd(clamp(plugin.getConfig().getInt("ai-planning.shelter.fallback-depth", 5), 3, 9));
    }

    private Material fallbackWallMaterial() {
        return safeMaterial(plugin.getConfig().getString("ai-planning.shelter.fallback-wall-material", "OAK_PLANKS"), Material.OAK_PLANKS);
    }

    private Material fallbackRoofMaterial() {
        return safeMaterial(plugin.getConfig().getString("ai-planning.shelter.fallback-roof-material", fallbackWallMaterial().name()), fallbackWallMaterial());
    }

    private int odd(int value) {
        return value % 2 == 0 ? Math.min(9, value + 1) : value;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String clean(String value) {
        return value != null ? value.replace('\n', ' ').replace('\r', ' ') : "";
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
}
