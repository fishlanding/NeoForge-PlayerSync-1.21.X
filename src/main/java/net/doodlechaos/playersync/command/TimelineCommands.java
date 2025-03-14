package net.doodlechaos.playersync.command;

import com.mojang.brigadier.CommandDispatcher;
import net.doodlechaos.playersync.PlayerSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class TimelineCommands {


    public static void registerPlayerSyncCommands(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
                Commands.literal("playersync")
                        .then(Commands.literal("debugOverlay")
                            .executes(ctx -> {
                                PlayerSync.debugTextOverlay = !PlayerSync.debugTextOverlay;
                                return 1;
                            })
                        )
        );
    }
}
