package net.doodlechaos.playersync;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;

@Mod(value = PlayerSync.MOD_ID, dist = Dist.CLIENT)
public class PlayerSyncClient {
    public PlayerSyncClient(ModContainer container) {
        // Register the built-in configuration screen so your config is editable in-game.
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
