package net.doodlechaos.playersync.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.doodlechaos.playersync.PlayerSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands; // Import NeoForge's Commands class

public class TickDeltaCommand {

    public static void registerTickDeltaCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tickDelta") // Use Commands.literal instead of Brigadier's literal
                        .then(Commands.argument("value", FloatArgumentType.floatArg())
                                .executes(context -> {
                                    float tickDelta = FloatArgumentType.getFloat(context, "value");
                                    PlayerSync.myTickDelta = tickDelta;
                                    context.getSource().sendSystemMessage(Component.literal("Set myTickDelta to: " + tickDelta));
                                    return 1; // Success
                                })
                        )
        );
    }
}