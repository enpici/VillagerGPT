package io.github.enpici.villager.life.integration;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import org.bukkit.Location;

import java.io.File;
import java.io.IOException;

public interface WorldEditGateway {

    Clipboard readClipboard(File schemFile) throws IOException;

    boolean pasteClipboard(Clipboard clipboard, Location destination, int maxSchematicBlocks, String blueprintId);
}
