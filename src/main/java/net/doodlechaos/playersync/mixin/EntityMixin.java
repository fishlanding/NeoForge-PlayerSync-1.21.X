package net.doodlechaos.playersync.mixin;

import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

    /**
     * This cancels any attempt to update the 'old' position fields (xo, yo, zo, xOld, yOld, zOld)
     * if we're in playback mode and not allowed to modify positions.
     */
    @Inject(method = "setOldPosAndRot", at = @At("HEAD"), cancellable = true)
    private void onSetOldPosAndRot(CallbackInfo ci) {
        if (!SyncTimeline.allowEntityMixinFlag && SyncTimeline.getMode() == SyncTimeline.TLMode.PLAYBACK) {
            ci.cancel();
        }
    }

    /**
     * This cancels the actual position updates (setPosRaw) if we're in playback mode
     * and not allowed to modify the entity position.
     */
    @Inject(method = "setPosRaw(DDD)V", at = @At("HEAD"), cancellable = true)
    private void onSetPosRaw(double x, double y, double z, CallbackInfo ci) {
        if (!SyncTimeline.allowEntityMixinFlag && SyncTimeline.getMode() == SyncTimeline.TLMode.PLAYBACK) {
            ci.cancel();
        }
    }
}
