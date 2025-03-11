package net.doodlechaos.playersync.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public class RecordCommands {

    public static void registerRecordCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("rec")
                // No argument version – uses default value 10
                .executes(ctx -> {
                    if(SyncTimeline.isRecording())
                    {
                        SyncTimeline.setRecording(false);
                        ctx.getSource().sendSystemMessage(Component.literal("Stopped Recording: " + SyncTimeline.isRecording()));
                    }
                    else{
                        SyncTimeline.startRecordingCountdown();
                        ctx.getSource().sendSystemMessage(Component.literal("Starting Recording: " + SyncTimeline.isRecording()));
                    }

                    return 1;
                })
                .then(literal("clear")
                        .executes(ctx -> {
                            SyncTimeline.clearRecordedKeyframes();
                            ctx.getSource().sendSystemMessage(Component.literal("Cleared recorded keyframes"));
                            return 1;
                        })
                )
                .then(literal("prune")
                        .executes(ctx -> {
                            int recKeysBefore = SyncTimeline.getRecordedKeyframes().size();
                            SyncTimeline.pruneKeyframesAfterPlayhead();
                            int recKeysAfter = SyncTimeline.getRecordedKeyframes().size();
                            ctx.getSource().sendSystemMessage(Component.literal(
                                    String.format("Cleared %d recorded keyframes", recKeysBefore - recKeysAfter)
                            ));
                            return 1;
                        })
                )
                .then(literal("save")
                        .then(Commands.argument("filename", StringArgumentType.string())
                                .executes(ctx -> {
                                    String filename = StringArgumentType.getString(ctx, "filename");

                                    SyncTimeline.SaveRecToFile(filename);
                                    ctx.getSource().sendSystemMessage(Component.literal("Saved " + filename));
                                    return 1;
                                })
                        )
                )
                .then(literal("load")
                        .then(Commands.argument("filename", StringArgumentType.string())
                                .executes(ctx -> {
                                    String filename = StringArgumentType.getString(ctx, "filename");

                                    SyncTimeline.LoadRecFromFile(filename);
                                    ctx.getSource().sendSystemMessage(Component.literal("Loaded " + filename));
                                    return 1;
                                })
                        )
                )
                .then(literal("testScreen")
                        .executes(ctx -> {
                            //PlayerSync.OpenScreen = !PlayerSync.OpenScreen;
                            //ctx.getSource().sendSystemMessage(Component.literal("Cleared recorded keyframes"));
                            return 1;
                        })
                )
        );
    }
}