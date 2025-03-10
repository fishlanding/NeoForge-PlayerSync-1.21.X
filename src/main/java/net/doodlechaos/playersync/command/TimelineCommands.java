package net.doodlechaos.playersync.command;

import com.mojang.brigadier.CommandDispatcher;
import net.doodlechaos.playersync.PlayerSync;
import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class TimelineCommands {


    public static void registerTimelineCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("activateTickLockstep")
                        .executes(context -> {

                            PlayerSync.tickLockstepEnabled = !PlayerSync.tickLockstepEnabled;
                            context.getSource().sendSystemMessage(Component.literal("set activateTickLockstep to: " + PlayerSync.tickLockstepEnabled));

                            return 1; // success
                        })
        );

        dispatcher.register(
                Commands.literal("playbackMode")
                        .executes(context -> {

                            SyncTimeline.setPlaybackEnabled(!SyncTimeline.isPlaybackEnabled());
                            context.getSource().sendSystemMessage(Component.literal("set activateTickLockstep to: " + PlayerSync.tickLockstepEnabled));

                            return 1; // success
                        })
        );
    }
}
