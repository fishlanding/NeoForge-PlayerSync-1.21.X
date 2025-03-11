package net.doodlechaos.playersync.mixin.accessor;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {

    @Invoker("setRotation")
    void invokeSetRotation(float yRot, float xRot, float roll);

    @Invoker("setPosition")
    void invokeSetPosition(Vec3 pos);
}
