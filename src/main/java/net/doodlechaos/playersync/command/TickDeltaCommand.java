package net.doodlechaos.playersync.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.doodlechaos.playersync.PlayerSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands; // Import NeoForge's Commands class

import static net.minecraft.commands.Commands.literal;

public class TickDeltaCommand {

    public static void registerTickDeltaCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("tickDelta")
                        .then(Commands.argument("overrideVal", FloatArgumentType.floatArg())
                                .executes(context -> {
                                    float tickDelta = FloatArgumentType.getFloat(context, "overrideVal");
                                    PlayerSync.myTickDelta = tickDelta;
                                    context.getSource().sendSystemMessage(Component.literal("Set overrideTickDelta to: " + tickDelta));
                                    return 1; // Success
                                })
                        )
                        .then(literal("override")
                                .executes(context -> {
                                    PlayerSync.overrideTickDelta = !PlayerSync.overrideTickDelta;
                                    context.getSource().sendSystemMessage(Component.literal("Set overrideTickDelta to: " + PlayerSync.overrideTickDelta));
                                    return 1; // Success
                                })
                        )
        );

    }
}