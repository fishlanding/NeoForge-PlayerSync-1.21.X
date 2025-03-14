package net.doodlechaos.playersync.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class TimelineCommands {


    public static void registerTestCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("timeline")
                        .then(Commands.literal("setframe")
                            .then(Commands.argument("frame", IntegerArgumentType.integer(0))
                                .executes(ctx -> {

                                    int frame = IntegerArgumentType.getInteger(ctx, "frame");
                                    SyncTimeline.setFrame(frame);

                                    ctx.getSource().sendSystemMessage(Component.literal("Set timeline frame to: " + frame));

                                    return 1;
                                })
                            )
                        )
        );

    }
}
