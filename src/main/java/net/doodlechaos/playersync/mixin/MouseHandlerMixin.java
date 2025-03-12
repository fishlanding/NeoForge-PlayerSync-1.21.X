package net.doodlechaos.playersync.mixin;

import net.doodlechaos.playersync.VideoRenderer;
import net.doodlechaos.playersync.input.InputsManager;
import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    //At the end of runTick
    @Inject(method = "onMove", at = @At("HEAD"), cancellable = false)
    private void onMouseMove(long windowPointer, double xpos, double ypos, CallbackInfo ci){
        InputsManager.onMouseMove(windowPointer, xpos, ypos);
    }

}
