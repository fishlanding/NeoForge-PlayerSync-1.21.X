package net.doodlechaos.playersync.command;

import com.google.common.collect.BoundType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.doodlechaos.playersync.PlayerSync;
import net.doodlechaos.playersync.sync.SyncKeyframe;
import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.neoforged.neoforge.event.level.NoteBlockEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static com.mojang.text2speech.Narrator.LOGGER;

public class TimelineCommands {


    public static void registerTimelineCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("activateTickLockstep")
                        .executes(context -> {

                            PlayerSync.activateTickLockstep = !PlayerSync.activateTickLockstep;
                            context.getSource().sendSystemMessage(Component.literal("set activateTickLockstep to: " + PlayerSync.activateTickLockstep));

                            return 1; // success
                        })
        );

        dispatcher.register(
                Commands.literal("playbackMode")
                        .executes(context -> {

                            SyncTimeline.setPlaybackEnabled(!SyncTimeline.isPlaybackEnabled());
                            context.getSource().sendSystemMessage(Component.literal("set activateTickLockstep to: " + PlayerSync.activateTickLockstep));

                            return 1; // success
                        })
        );
    }
}
