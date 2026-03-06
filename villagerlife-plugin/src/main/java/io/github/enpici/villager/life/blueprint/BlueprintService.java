package io.github.enpici.villager.life.blueprint;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlueprintService {

    private final JavaPlugin plugin;
    private final Map<String, File> blueprints = new ConcurrentHashMap<>();

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
            String id = file.getName().replace(".schem", "").toLowerCase();
            blueprints.put(id, file);
        }
    }

    public boolean hasBlueprint(String id) {
        return blueprints.containsKey(id.toLowerCase());
    }

    public boolean placeInstant(String id, Location location) {
        if (location == null) {
            return false;
        }
        return hasBlueprint(id);
    }
}
