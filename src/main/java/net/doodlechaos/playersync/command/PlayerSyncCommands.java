package net.doodlechaos.playersync.command;

import com.mojang.brigadier.CommandDispatcher;
import net.doodlechaos.playersync.Config;
import net.doodlechaos.playersync.PlayerSync;
import net.doodlechaos.playersync.sync.AudioSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class PlayerSyncCommands {


    public static void registerPlayerSyncCommands(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
                Commands.literal("playersync")
                        .then(Commands.literal("debugOverlay")
                            .executes(ctx -> {
                                PlayerSync.debugTextOverlay = !PlayerSync.debugTextOverlay;
                                return 1;
                            })
                        )
                        .then(Commands.literal("reloadAudio")
                                .executes(ctx -> {
                                    String inputAudioPath = Config.CONFIG.inputAudioPathOgg.get();
                                    AudioSync.loadAudio(inputAudioPath);
                                    return 1;
                                })
                        )
        );
    }
}
