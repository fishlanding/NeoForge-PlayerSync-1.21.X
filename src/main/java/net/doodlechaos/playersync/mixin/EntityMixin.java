package net.doodlechaos.playersync.mixin;

import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.doodlechaos.playersync.sync.SyncTimeline.TLMode;

import static net.doodlechaos.playersync.PlayerSync.SLOGGER;

@Mixin(Entity.class)
public class EntityMixin {

    //It's crucial that I only block this on the client side, not the server side. Otherwise the server side position never updates during playback
    @SuppressWarnings("ConstantConditions")
    @Inject(method = "setOldPosAndRot", at = @At("HEAD"), cancellable = true)
    private void onSetOldPosAndRot(CallbackInfo ci) {
        if (!SyncTimeline.allowEntityMixinFlag
                && SyncTimeline.getMode() == TLMode.PLAYBACK
                && !SyncTimeline.isPlaybackDetached()
                && ((Entity)(Object)this).getType().equals(EntityType.PLAYER)
                && ((Entity)(Object)this).level().isClientSide()) {
            ci.cancel();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Inject(method = "setPosRaw(DDD)V", at = @At("HEAD"), cancellable = true)
    private void onSetPosRaw(double x, double y, double z, CallbackInfo ci) {
        if (!SyncTimeline.allowEntityMixinFlag
                && SyncTimeline.getMode() == TLMode.PLAYBACK
                && !SyncTimeline.isPlaybackDetached()
                && ((Entity)(Object)this).getType().equals(EntityType.PLAYER)
                && ((Entity)(Object)this).level().isClientSide()) {

            ci.cancel();
        }
    }

}
