package io.github.enpici.villager.life.blueprint;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.event.VillageStructureBuiltEvent;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BlueprintService {

    private static final int MAX_IO_RETRIES = 2;
    private static final int MAX_WORLD_RETRIES = 2;
    private static final int MAX_SCHEMATIC_BLOCKS = 1_000_000;

    private final JavaPlugin plugin;
    private final BuildTelemetry buildTelemetry;
    private final Map<String, BlueprintDefinition> blueprints = new ConcurrentHashMap<>();

    public BlueprintService(JavaPlugin plugin, BuildTelemetry buildTelemetry) {
        this.plugin = plugin;
        this.buildTelemetry = buildTelemetry;
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

    public boolean placeStructure(String id, VillageAI village, Agent builder, Location location) {
        long startedAt = System.currentTimeMillis();
        String normalizedId = id != null ? id.toLowerCase(Locale.ROOT) : null;
        java.util.UUID villageId = village != null ? village.id() : null;
        java.util.UUID agentUuid = builder != null ? builder.villagerUuid() : null;
        buildTelemetry.logBuildStarted(villageId, agentUuid, normalizedId);

        if (location == null || village == null || id == null || id.isBlank()) {
            String reason = "invalid_arguments";
            plugin.getLogger().warning("Build cancelado: argumentos inválidos para placeStructure.");
            buildTelemetry.logBuildFailed(villageId, agentUuid, normalizedId, 0, elapsed(startedAt), reason);
            return false;
        }

        Optional<BlueprintDefinition> definitionOpt = find(id);
        if (definitionOpt.isEmpty()) {
            String reason = "blueprint_not_found";
            plugin.getLogger().warning("Build cancelado: blueprint no encontrado: " + id);
            buildTelemetry.logBuildFailed(villageId, agentUuid, normalizedId, 0, elapsed(startedAt), reason);
            return false;
        }

        BuildMode mode = resolveBuildMode();
        if (mode == BuildMode.GRANULAR) {
            String reason = "granular_not_implemented";
            plugin.getLogger().warning("build.mode=GRANULAR todavía no implementado. Saltando build de " + id);
            buildTelemetry.logBuildFailed(villageId, agentUuid, normalizedId, 0, elapsed(startedAt), reason);
            return false;
        }

        buildTelemetry.logBuildStep(villageId, agentUuid, normalizedId, 1, elapsed(startedAt));
        boolean pasted = pasteInstant(definitionOpt.get(), village, builder, location, startedAt, villageId, agentUuid);
        if (pasted) {
            buildTelemetry.logBuildCompleted(villageId, agentUuid, normalizedId, 4, elapsed(startedAt));
            Bukkit.getPluginManager().callEvent(new VillageStructureBuiltEvent(village, id));
        }
        return pasted;
    }

    private boolean pasteInstant(BlueprintDefinition definition, VillageAI village, Agent builder, Location destination,
                                 long startedAt, java.util.UUID villageId, java.util.UUID agentUuid) {
        buildTelemetry.logBuildStep(villageId, agentUuid, definition.id(), 2, elapsed(startedAt));
        if (!validateWorldAndChunk(destination)) {
            buildTelemetry.logBuildFailed(villageId, agentUuid, definition.id(), 2, elapsed(startedAt), "world_or_chunk_unavailable");
            return false;
        }
        if (!validateRegionEditPermissions(destination)) {
            buildTelemetry.logBuildFailed(villageId, agentUuid, definition.id(), 2, elapsed(startedAt), "region_denied");
            return false;
        }

        for (int attempt = 1; attempt <= MAX_IO_RETRIES + 1; attempt++) {
            try {
                Clipboard clipboard = readClipboard(definition.schemFile());
                buildTelemetry.logBuildStep(villageId, agentUuid, definition.id(), 3, elapsed(startedAt));
                if (!validateSchematicSize(clipboard, definition.id())) {
                    buildTelemetry.logBuildFailed(villageId, agentUuid, definition.id(), 3, elapsed(startedAt), "schematic_too_large");
                    return false;
                }

                Map<Material, Integer> requiredMaterials = collectRequiredMaterials(clipboard);
                MaterialConsumptionResult consumption = consumeMaterials(requiredMaterials, village, builder);
                if (!consumption.successful()) {
                    buildTelemetry.logBuildFailed(villageId, agentUuid, definition.id(), 3, elapsed(startedAt), "insufficient_materials_or_sources");
                    return false;
                }

                buildTelemetry.logBuildStep(villageId, agentUuid, definition.id(), 4, elapsed(startedAt));
                boolean pasted = pasteClipboard(clipboard, destination, definition.id());
                if (!pasted) {
                    rollbackConsumption(consumption.consumedItems());
                    buildTelemetry.logBuildFailed(villageId, agentUuid, definition.id(), 4, elapsed(startedAt), "worldedit_paste_failed");
                }
                return pasted;
            } catch (IOException ioException) {
                plugin.getLogger().warning("Error I/O leyendo schematic '" + definition.id() + "' intento "
                        + attempt + "/" + (MAX_IO_RETRIES + 1) + ": " + ioException.getMessage());
                if (attempt <= MAX_IO_RETRIES) {
                    buildTelemetry.incrementRetry();
                }
                if (attempt > MAX_IO_RETRIES) {
                    plugin.getLogger().severe("Build cancelado: schematic corrupto o ilegible para '" + definition.id() + "'.");
                    buildTelemetry.logBuildFailed(villageId, agentUuid, definition.id(), 3, elapsed(startedAt), "schematic_io_error");
                    return false;
                }
            }
        }
        return false;
    }

    private Clipboard readClipboard(File schemFile) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
        if (format == null) {
            throw new IOException("Formato de schematic no soportado: " + schemFile.getName());
        }

        try (FileInputStream input = new FileInputStream(schemFile);
             ClipboardReader reader = format.getReader(input)) {
            return reader.read();
        }
    }

    private Map<Material, Integer> collectRequiredMaterials(Clipboard clipboard) {
        Map<Material, Integer> required = new HashMap<>();
        for (BlockVector3 position : clipboard.getRegion()) {
            var block = clipboard.getBlock(position);
            Material material;
            try {
                material = BukkitAdapter.adapt(block.getBlockType());
            } catch (Exception ignored) {
                continue;
            }
            if (material == null || material.isAir()) {
                continue;
            }
            required.merge(material, 1, Integer::sum);
        }
        return required;
    }

    private MaterialConsumptionResult consumeMaterials(Map<Material, Integer> requiredMaterials, VillageAI village, Agent builder) {
        if (requiredMaterials.isEmpty()) {
            return MaterialConsumptionResult.success(new ArrayList<>());
        }

        List<Inventory> sources = collectMaterialSources(village, builder);
        if (sources.isEmpty()) {
            plugin.getLogger().warning("Build cancelado: no hay inventarios/cofres configurados para consumir materiales.");
            return MaterialConsumptionResult.failure();
        }

        Map<Material, Integer> available = countAvailableMaterials(sources);
        Map<Material, Integer> missing = new HashMap<>();
        for (Map.Entry<Material, Integer> entry : requiredMaterials.entrySet()) {
            int availableAmount = available.getOrDefault(entry.getKey(), 0);
            if (availableAmount < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - availableAmount);
            }
        }
        if (!missing.isEmpty()) {
            plugin.getLogger().warning("Build cancelado: materiales insuficientes. Faltante=" + missing);
            return MaterialConsumptionResult.failure();
        }

        List<ConsumedItem> consumedItems = new ArrayList<>();
        for (Map.Entry<Material, Integer> requirement : requiredMaterials.entrySet()) {
            int pending = requirement.getValue();
            for (Inventory source : sources) {
                pending -= consumeFromInventory(source, requirement.getKey(), pending, consumedItems);
                if (pending <= 0) {
                    break;
                }
            }
            if (pending > 0) {
                rollbackConsumption(consumedItems);
                plugin.getLogger().warning("Build cancelado: no se pudo descontar material " + requirement.getKey());
                return MaterialConsumptionResult.failure();
            }
        }
        return MaterialConsumptionResult.success(consumedItems);
    }

    private List<Inventory> collectMaterialSources(VillageAI village, Agent builder) {
        List<Inventory> sources = new ArrayList<>();

        if (builder != null) {
            Villager villager = builder.resolveVillager();
            if (villager != null) {
                sources.add(villager.getInventory());
            }
        }

        Set<String> seen = new HashSet<>();
        List<String> configuredSources = plugin.getConfig().getStringList("build.material-sources");
        for (String raw : configuredSources) {
            Location location = parseLocation(raw, village.center().getWorld() != null ? village.center().getWorld().getName() : null);
            if (location == null || location.getWorld() == null) {
                continue;
            }
            String key = location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
            if (!seen.add(key)) {
                continue;
            }
            if (location.getBlock().getState() instanceof Container container) {
                sources.add(container.getInventory());
            }
        }

        int radius = Math.max(1, plugin.getConfig().getInt("build.nearby-container-radius", 16));
        Location center = village.center();
        if (center.getWorld() != null) {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -4; y <= 4; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Location location = center.clone().add(x, y, z);
                        if (!(location.getBlock().getState() instanceof Container container)) {
                            continue;
                        }
                        String key = location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
                        if (seen.add(key)) {
                            sources.add(container.getInventory());
                        }
                    }
                }
            }
        }

        return sources;
    }

    private Location parseLocation(String raw, String defaultWorld) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        try {
            if (parts.length == 4) {
                var world = Bukkit.getWorld(parts[0].trim());
                if (world == null) {
                    return null;
                }
                return new Location(world, Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
            }
            if (parts.length == 3 && defaultWorld != null) {
                var world = Bukkit.getWorld(defaultWorld);
                if (world == null) {
                    return null;
                }
                return new Location(world, Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    private Map<Material, Integer> countAvailableMaterials(List<Inventory> sources) {
        Map<Material, Integer> available = new HashMap<>();
        for (Inventory source : sources) {
            for (ItemStack item : source.getContents()) {
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                available.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }
        return available;
    }

    private int consumeFromInventory(Inventory inventory, Material material, int requested, List<ConsumedItem> consumedItems) {
        if (requested <= 0) {
            return 0;
        }

        int consumed = 0;
        for (int slot = 0; slot < inventory.getSize() && consumed < requested; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != material) {
                continue;
            }
            int toTake = Math.min(requested - consumed, item.getAmount());
            ItemStack consumedStack = item.clone();
            consumedStack.setAmount(toTake);
            consumedItems.add(new ConsumedItem(inventory, consumedStack));

            if (item.getAmount() == toTake) {
                inventory.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - toTake);
                inventory.setItem(slot, item);
            }
            consumed += toTake;
        }
        return consumed;
    }

    private void rollbackConsumption(List<ConsumedItem> consumedItems) {
        for (ConsumedItem consumedItem : consumedItems) {
            Map<Integer, ItemStack> leftovers = consumedItem.inventory().addItem(consumedItem.item());
            if (!leftovers.isEmpty()) {
                plugin.getLogger().warning("Rollback parcial de materiales: no caben todos los items devueltos.");
            }
        }
    }

    private boolean pasteClipboard(Clipboard clipboard, Location destination, String blueprintId) {
        World world = BukkitAdapter.adapt(destination.getWorld());
        BlockVector3 to = BlockVector3.at(destination.getBlockX(), destination.getBlockY(), destination.getBlockZ());

        try (EditSession editSession = WorldEdit.getInstance()
                .newEditSessionBuilder()
                .world(world)
                .maxBlocks(MAX_SCHEMATIC_BLOCKS)
                .build()) {
            Operation operation = new com.sk89q.worldedit.session.ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(to)
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(operation);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().severe("Falló paste WE para blueprint '" + blueprintId + "': " + exception.getMessage());
            return false;
        }
    }

    private boolean validateWorldAndChunk(Location destination) {
        if (destination.getWorld() == null) {
            plugin.getLogger().warning("Build cancelado: mundo destino nulo/no cargado.");
            return false;
        }

        Chunk chunk = destination.getChunk();
        for (int attempt = 1; attempt <= MAX_WORLD_RETRIES + 1; attempt++) {
            if (chunk.isLoaded() || chunk.load()) {
                return true;
            }
            plugin.getLogger().warning("Chunk destino no cargado. Reintento " + attempt + "/" + (MAX_WORLD_RETRIES + 1));
        }

        plugin.getLogger().warning("Build cancelado: no se pudo cargar chunk destino.");
        return false;
    }

    private boolean validateRegionEditPermissions(Location destination) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            return true;
        }

        plugin.getLogger().info("WorldGuard detectado: validación de región no implementada en esta versión, se permite build por compatibilidad.");
        return true;
    }

    private boolean validateSchematicSize(Clipboard clipboard, String blueprintId) {
        BlockVector3 dimensions = clipboard.getDimensions();
        long totalBlocks = (long) dimensions.x() * dimensions.y() * dimensions.z();
        if (totalBlocks > MAX_SCHEMATIC_BLOCKS) {
            plugin.getLogger().warning("Build cancelado: schematic '" + blueprintId + "' excede límite de tamaño ("
                    + totalBlocks + " > " + MAX_SCHEMATIC_BLOCKS + ").");
            return false;
        }
        return true;
    }


    public BuildTelemetry telemetry() {
        return buildTelemetry;
    }

    private long elapsed(long startedAt) {
        return Math.max(0L, System.currentTimeMillis() - startedAt);
    }

    private BuildMode resolveBuildMode() {
        String raw = plugin.getConfig().getString("build.mode", BuildMode.INSTANT.name());
        try {
            return BuildMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("build.mode inválido '" + raw + "'. Se usa INSTANT.");
            return BuildMode.INSTANT;
        }
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

    private enum BuildMode {
        INSTANT,
        GRANULAR
    }

    private record ConsumedItem(Inventory inventory, ItemStack item) {
    }

    private record MaterialConsumptionResult(boolean successful, List<ConsumedItem> consumedItems) {
        private static MaterialConsumptionResult success(List<ConsumedItem> consumedItems) {
            return new MaterialConsumptionResult(true, consumedItems);
        }

        private static MaterialConsumptionResult failure() {
            return new MaterialConsumptionResult(false, List.of());
        }
    }
}
