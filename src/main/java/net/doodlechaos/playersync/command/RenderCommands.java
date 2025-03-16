package net.doodlechaos.playersync.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.doodlechaos.playersync.VideoRenderer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public class RenderCommands {

    public static void registerRenderCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("render")
                // No argument version – uses default value 10
                .executes(ctx -> {
                    VideoRenderer.StartRendering();

                    ctx.getSource().sendSystemMessage(Component.literal("Started rendering!"));
                    return 1;
                })
                .then(literal("setPreFrameWaitCount")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 10))
                                .executes(ctx -> {
                                    int count = IntegerArgumentType.getInteger(ctx, "count");
                                    VideoRenderer.preFrameWaitCount = count;
                                    ctx.getSource().sendSystemMessage(Component.literal("set pre frame wait count to: " + count));
                                    return 1;
                                })
                        )
                        .executes(ctx -> {
                            ctx.getSource().sendSystemMessage(Component.literal("Curr preFrameWaitCount: " + VideoRenderer.preFrameWaitCount));
                            return 1;
                        })
                )

        );
    }
}