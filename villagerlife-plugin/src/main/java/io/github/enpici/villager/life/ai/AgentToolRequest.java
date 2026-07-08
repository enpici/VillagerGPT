package io.github.enpici.villager.life.ai;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record AgentToolRequest(
        AgentTool tool,
        Material material,
        Integer amount,
        String target,
        String reason
) {

    private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern INT_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(\\d+)");
    private static final Pattern TOOL_OBJECT = Pattern.compile("\"tool\"\\s*:\\s*(\\{.*?})\\s*(?:,\\s*\"|})", Pattern.DOTALL);

    public static Optional<AgentToolRequest> parseFromPlanJson(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        Matcher objectMatcher = TOOL_OBJECT.matcher(json);
        String toolJson;
        if (objectMatcher.find()) {
            toolJson = objectMatcher.group(1);
        } else {
            String name = readString(json, "tool", "");
            if (name.isBlank()) {
                return Optional.empty();
            }
            toolJson = json;
        }

        String rawName = firstNonBlank(
                readString(toolJson, "name", ""),
                readString(toolJson, "tool", ""),
                readString(toolJson, "action", "")
        );
        AgentTool tool = parseTool(rawName);
        if (tool == null) {
            return Optional.empty();
        }

        String rawMaterial = firstNonBlank(
                readString(toolJson, "material", ""),
                readString(toolJson, "item", ""),
                readString(toolJson, "block", ""),
                readString(toolJson, "target", "")
        );
        return Optional.of(new AgentToolRequest(
                tool,
                parseMaterial(rawMaterial),
                readOptionalInt(toolJson, "amount"),
                clean(readString(toolJson, "target", "")),
                clean(readString(toolJson, "reason", ""))
        ));
    }

    public String compact() {
        StringBuilder builder = new StringBuilder(tool.name().toLowerCase(Locale.ROOT));
        if (material != null) {
            builder.append(":").append(material.name().toLowerCase(Locale.ROOT));
        } else if (target != null && !target.isBlank()) {
            builder.append(":").append(target.toLowerCase(Locale.ROOT));
        }
        if (amount != null && amount > 0) {
            builder.append("x").append(amount);
        }
        return builder.toString();
    }

    public Map<String, String> args() {
        java.util.LinkedHashMap<String, String> args = new java.util.LinkedHashMap<>();
        if (material != null) {
            args.put("material", material.name());
        }
        if (amount != null) {
            args.put("amount", String.valueOf(amount));
        }
        if (target != null && !target.isBlank()) {
            args.put("target", target);
        }
        return Map.copyOf(args);
    }

    private static AgentTool parseTool(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return AgentTool.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return Material.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String readString(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile(STRING_FIELD.pattern().formatted(Pattern.quote(key))).matcher(json != null ? json : "");
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static Integer readOptionalInt(String json, String key) {
        Matcher matcher = Pattern.compile(INT_FIELD.pattern().formatted(Pattern.quote(key))).matcher(json != null ? json : "");
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replace('"', '\'').trim();
    }
}
