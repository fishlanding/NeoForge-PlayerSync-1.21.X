package net.doodlechaos.playersync.mixin;

import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.doodlechaos.playersync.sync.SyncTimeline.TLMode;

@Mixin(Entity.class)
public class EntityMixin {

    @SuppressWarnings("ConstantConditions")
    @Inject(method = "setOldPosAndRot", at = @At("HEAD"), cancellable = true)
    private void onSetOldPosAndRot(CallbackInfo ci) {
        if (!SyncTimeline.allowEntityMixinFlag
                && SyncTimeline.getMode() == TLMode.PLAYBACK
                && !SyncTimeline.isPlaybackDetached()
                && ((Entity)(Object)this).getType().equals(EntityType.PLAYER)) {
            ci.cancel();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Inject(method = "setPosRaw(DDD)V", at = @At("HEAD"), cancellable = true)
    private void onSetPosRaw(double x, double y, double z, CallbackInfo ci) {
        if (!SyncTimeline.allowEntityMixinFlag
                && SyncTimeline.getMode() == TLMode.PLAYBACK
                && !SyncTimeline.isPlaybackDetached()
                && ((Entity)(Object)this).getType().equals(EntityType.PLAYER)) {
            ci.cancel();
        }
    }

}
