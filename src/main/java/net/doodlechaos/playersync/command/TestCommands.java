package net.doodlechaos.playersync.command;

import com.mojang.brigadier.CommandDispatcher;
import net.doodlechaos.playersync.PlayerSync;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.player.Player;

public class TestCommands {


    public static void registerTestCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("testSetPlayerPos")
                        .executes(ctx -> {
                            Player player = Minecraft.getInstance().player;

                            player.xo = -16;
                            player.yo = 64;
                            player.zo = 176;

                            player.setPos(-16, 64, 177);

                            return 1;
                        })
        );

    }
}
