package net.doodlechaos.playersync.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;

public class PlayerSyncFolderUtils {

    public static File getPlayerSyncFolder() {
        Minecraft client = Minecraft.getInstance();
        File baseFolder;
        if (client.getSingleplayerServer() != null) {
            baseFolder = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toFile();
        } else {
            baseFolder = client.gameDirectory;
        }
        File folder = new File(baseFolder, "playersync");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

}
