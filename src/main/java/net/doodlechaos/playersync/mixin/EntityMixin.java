package net.doodlechaos.playersync.mixin;

import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Entity.class)
public class EntityMixin {

    //It's crucial that I only block this on the client side, not the server side. Otherwise the server side position never updates during playback
    @SuppressWarnings("ConstantConditions")
    @Inject(method = "setOldPosAndRot", at = @At("HEAD"), cancellable = true)
    private void onSetOldPosAndRot(CallbackInfo ci) {

/*        if(SyncTimeline.getMode() == SyncTimeline.TLMode.REC_COUNTDOWN && ((Entity)(Object)this).getType().equals(EntityType.PLAYER)){
                if(SyncTimeline.isTickFrame() && SyncTimeline.getCountdownFramesRemaining() <= 3) //IMPORTANT: This line is neccessary for smooth transition from playback to recording
                {
                    ci.cancel();
                    return;
                }
        }*/

        if (!SyncTimeline.allowEntityMixinFlag
                && SyncTimeline.isSomeFormOfPlayback()
                && !SyncTimeline.isPlaybackDetached()
                && ((Entity)(Object)this).getType().equals(EntityType.PLAYER)
                && ((Entity)(Object)this).level().isClientSide()) {
            ci.cancel();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Inject(method = "setPosRaw(DDD)V", at = @At("HEAD"), cancellable = true)
    private void onSetPosRaw(double x, double y, double z, CallbackInfo ci) {

/*        if(SyncTimeline.getMode() == SyncTimeline.TLMode.REC_COUNTDOWN && ((Entity)(Object)this).getType().equals(EntityType.PLAYER)){
            if(SyncTimeline.isTickFrame() && SyncTimeline.getCountdownFramesRemaining() <= 3) //IMPORTANT: This line is neccessary for smooth transition from playback to recording
            {
                ci.cancel();
                return;
            }
        }*/

        if (!SyncTimeline.allowEntityMixinFlag
                && SyncTimeline.isSomeFormOfPlayback()
                && !SyncTimeline.isPlaybackDetached()
                && ((Entity)(Object)this).getType().equals(EntityType.PLAYER)
                && ((Entity)(Object)this).level().isClientSide()) {

            ci.cancel();
        }
    }

}
