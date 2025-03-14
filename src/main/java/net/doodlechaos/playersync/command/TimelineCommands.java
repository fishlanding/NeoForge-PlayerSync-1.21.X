package net.doodlechaos.playersync.command;

import com.mojang.brigadier.CommandDispatcher;
import net.doodlechaos.playersync.PlayerSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class TimelineCommands {


    public static void registerTimelineCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("testScreen")
                        .executes(ctx -> {
                            PlayerSync.OpenKeyCommandsEditScreen = !PlayerSync.OpenKeyCommandsEditScreen;
                            return 1;
                        })
        );

    }
}
