package io.github.enpici.villager.life.blueprint;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BlueprintService {

    private final JavaPlugin plugin;
    private final Map<String, BlueprintDefinition> blueprints = new ConcurrentHashMap<>();

    public BlueprintService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadFromDisk() {
        File folder = new File(plugin.getDataFolder(), "blueprints");
        if (!folder.exists() && !folder.mkdirs()) {
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".schem"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            String id = file.getName().replace(".schem", "").toLowerCase(Locale.ROOT);
            blueprints.put(id, loadDefinition(id, file));
        }
    }

    public boolean hasBlueprint(String id) {
        return blueprints.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public Optional<BlueprintDefinition> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(blueprints.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<BlueprintDefinition> findFirstByType(BuildingType type) {
        return blueprints.values().stream()
                .filter(definition -> definition.type() == type)
                .findFirst();
    }

    public boolean placeInstant(String id, Location location) {
        if (location == null) {
            return false;
        }
        return hasBlueprint(id);
    }

    private BlueprintDefinition loadDefinition(String id, File schemFile) {
        File metadata = new File(schemFile.getParentFile(), id + ".yml");
        if (!metadata.exists()) {
            return fromNameHeuristics(id, schemFile);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(metadata);
        String typeRaw = yaml.getString("type", "GENERIC");
        BuildingType type = BuildingType.from(typeRaw);
        int capacity = Math.max(0, yaml.getInt("capacity", 0));
        Set<String> tags = yaml.getStringList("tags").stream()
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        return new BlueprintDefinition(id, schemFile, type, capacity, tags);
    }

    private BlueprintDefinition fromNameHeuristics(String id, File schemFile) {
        String normalized = id.toLowerCase(Locale.ROOT);
        Set<String> tags = Arrays.stream(normalized.split("[_\\-]"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());

        BuildingType type;
        if (normalized.contains("house") || normalized.contains("home")) {
            type = BuildingType.HOUSE;
        } else if (normalized.contains("food") || normalized.contains("farm") || normalized.contains("granary")) {
            type = BuildingType.FOOD_STORAGE;
        } else if (normalized.contains("warehouse") || normalized.contains("storage") || normalized.contains("stock")) {
            type = BuildingType.MATERIAL_STORAGE;
        } else if (normalized.contains("work") || normalized.contains("smith") || normalized.contains("craft")) {
            type = BuildingType.WORKSTATION_HUB;
        } else if (normalized.contains("wall") || normalized.contains("tower") || normalized.contains("guard")) {
            type = BuildingType.DEFENSIVE;
        } else {
            type = BuildingType.GENERIC;
        }

        return new BlueprintDefinition(id, schemFile, type, 0, tags);
    }
}
