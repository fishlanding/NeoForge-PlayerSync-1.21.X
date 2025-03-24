package net.doodlechaos.playersync.mixin;

import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    /**
     * Redirects the call to {@code this.tickServer(flag ? () -> false : this::haveTime)}
     * inside {@code runServer()}. If the SyncTimeline conditions are met, the call is skipped.
     *
     * Otherwise, it calls {@code tickServer} as usual.
     */
    @Redirect(
            method = "runServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V"
            )
    )
    private void playersync_redirectTickServer(MinecraftServer server, BooleanSupplier hasTimeLeft) {

        if (SyncTimeline.isSomeFormOfPlayback())
            return;  // Skip tickServer call entirely

        // Otherwise, call original
        server.tickServer(hasTimeLeft);
    }

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
