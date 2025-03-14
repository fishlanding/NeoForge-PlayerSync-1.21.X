package net.doodlechaos.playersync.mixin;

import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
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

/*    @SuppressWarnings("ConstantConditions")
    @Inject(method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
    private void onSetDeltaMovement(Vec3 deltaMovement, CallbackInfo ci) {
        boolean flag1 = !SyncTimeline.allowEntityMixinFlag;
        boolean flag2 = SyncTimeline.getMode() == TLMode.PLAYBACK;
        boolean flag3 = !SyncTimeline.isPlaybackDetached();
        boolean flag4 = ((Entity)(Object)this).getType().equals(EntityType.PLAYER);
        if (flag1
                && flag2
                && flag3
                && flag4) //Triggers on client and server
        {
            ci.cancel();
            return;
        }
        if(flag4)
            SLOGGER.info("Setting player delta movement to: " + deltaMovement);
    }*/

}
