package net.doodlechaos.playersync.mixin;

import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

//Tested: This mixin is necessary!
@Mixin(Camera.class)
public class CameraMixin {

    @ModifyVariable(method = "setRotation(FFF)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float modifyYaw(float yRot) {
        if (SyncTimeline.isSomeFormOfPlayback() && !SyncTimeline.isPlaybackDetached()) {
            return SyncTimeline.keyframeCamEulerDegrees.y;
        }
        return yRot;
    }

    @ModifyVariable(method = "setRotation(FFF)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float modifyPitch(float xRot) {
        if (SyncTimeline.isSomeFormOfPlayback() && !SyncTimeline.isPlaybackDetached()) {
            return SyncTimeline.keyframeCamEulerDegrees.x;
        }
        return xRot;
    }

    @ModifyVariable(method = "setRotation(FFF)V", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private float modifyRoll(float roll) {
        if (SyncTimeline.isSomeFormOfPlayback() && !SyncTimeline.isPlaybackDetached()) {
            return SyncTimeline.keyframeCamEulerDegrees.z;
        }
        return roll;
    }

}
