package net.doodlechaos.playersync.mixin;


import net.doodlechaos.playersync.input.InputsManager;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.doodlechaos.playersync.PlayerSync.SLOGGER;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "handleChatInput", at = @At("TAIL"))
    private void onHandleChatInput(String message, boolean addToRecentChat, CallbackInfo ci){
        if (message.startsWith("/")) {
            InputsManager.mostRecentCommand = message;
            SLOGGER.info("Captured command from ChatScreen: " + message);
        }
        SLOGGER.info("Detected on send message in chat screen: " + message);
    }

}
