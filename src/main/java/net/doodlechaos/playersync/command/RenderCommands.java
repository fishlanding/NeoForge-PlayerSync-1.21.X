package net.doodlechaos.playersync.command;

import com.mojang.brigadier.CommandDispatcher;
import net.doodlechaos.playersync.VideoRenderer;
import net.minecraft.commands.CommandSourceStack;
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
                }));
    }
}