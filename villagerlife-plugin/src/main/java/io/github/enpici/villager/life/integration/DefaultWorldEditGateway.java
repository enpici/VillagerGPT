package io.github.enpici.villager.life.integration;

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
import org.bukkit.Location;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class DefaultWorldEditGateway implements WorldEditGateway {

    @Override
    public Clipboard readClipboard(File schemFile) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
        if (format == null) {
            throw new IOException("Formato schematic no soportado: " + schemFile.getName());
        }

        try (FileInputStream inputStream = new FileInputStream(schemFile);
             ClipboardReader reader = format.getReader(inputStream)) {
            return reader.read();
        }
    }

    @Override
    public boolean pasteClipboard(Clipboard clipboard, Location destination, int maxSchematicBlocks, String blueprintId) {
        World world = BukkitAdapter.adapt(destination.getWorld());
        BlockVector3 to = BlockVector3.at(destination.getBlockX(), destination.getBlockY(), destination.getBlockZ());

        try (EditSession editSession = WorldEdit.getInstance()
                .newEditSessionBuilder()
                .world(world)
                .maxBlocks(maxSchematicBlocks)
                .build()) {
            Operation operation = new com.sk89q.worldedit.session.ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(to)
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(operation);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
