package net.doodlechaos.playersync.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.doodlechaos.playersync.Config;
import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static net.doodlechaos.playersync.sync.SyncTimeline.TLMode;
import static net.minecraft.commands.Commands.literal;

public class RecordCommands {

    public static void registerRecordCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("rec")
                // No argument version – uses default value 10
                .executes(ctx -> {
                    if(SyncTimeline.getMode() == TLMode.REC)
                    {
                        SyncTimeline.setCurrMode(TLMode.NONE, true);
                        ctx.getSource().sendSystemMessage(Component.literal("Stopped Recording"));
                    }
                    else{
                        SyncTimeline.setCurrMode(TLMode.REC_COUNTDOWN, false);
                        ctx.getSource().sendSystemMessage(Component.literal("Starting Countdown"));
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

                                    boolean success = SyncTimeline.LoadRecFromFile(filename);
                                    if(success)
                                        ctx.getSource().sendSystemMessage(Component.literal("Loaded " + filename));
                                    else
                                        ctx.getSource().sendSystemMessage(Component.literal("FAILED to load " + filename));
                                    return 1;
                                })
                        )
                )
                .then(literal("setCountdownDuration")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(3))
                            .executes(ctx -> {
                                int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                Config.CONFIG.recCountdownDurationFramesTotal.set(60 * seconds);
                                ctx.getSource().sendSystemMessage(Component.literal("Set countdown duration frames to: " + Config.CONFIG.recCountdownDurationFramesTotal.get()));
                                return 1;
                            })
                        )
                        .executes(ctx -> {
                            ctx.getSource().sendSystemMessage(Component.literal("Current countdown duration frames: " + Config.CONFIG.recCountdownDurationFramesTotal.get()));
                            return 1;
                        })

                )
                .then(literal("undoLastRecSession")
                        .executes(ctx -> {
                            String resp = SyncTimeline.undoLastRecSession();
                            ctx.getSource().sendSystemMessage(Component.literal(resp));
                            return 1;
                        })
                )
        );
    }
}