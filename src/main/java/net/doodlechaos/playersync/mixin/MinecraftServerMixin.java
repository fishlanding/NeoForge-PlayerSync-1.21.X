package net.doodlechaos.playersync.mixin;

import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

/*    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void onTickServer(BooleanSupplier hasTimeLeft, CallbackInfo ci){
        if (!SyncTimeline.allowTickServerFlag
            && SyncTimeline.getMode() == SyncTimeline.TLMode.PLAYBACK
            && !SyncTimeline.isPlaybackDetached())
        {
            ci.cancel();
        }
    }*/

}
