package net.doodlechaos.playersync.mixin;

import net.doodlechaos.playersync.PlayerSync;
import net.doodlechaos.playersync.mixin.accessor.BlockableEventLoopAccessor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;

import java.util.concurrent.CountDownLatch;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Unique
    private boolean cachedRenderLevel;

    @ModifyVariable(method = "runTick(Z)V", at = @At("HEAD"), argsOnly = true)
    private boolean modifyRenderLevel(boolean renderLevel) {
        cachedRenderLevel = renderLevel;
        // Override the renderLevel parameter if tick lockstep is active
        return PlayerSync.activateTickLockstep ? false : renderLevel;
    }

    @Redirect(
            method = "runTick(Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/DeltaTracker$Timer;advanceTime(JZ)I"
            )
    )
    private int redirectAdvanceTime(DeltaTracker.Timer timer, long timeMillis, boolean renderLevel) {
        if (!PlayerSync.activateTickLockstep)
            return timer.advanceTime(timeMillis, renderLevel);

        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        // If the integrated server exists, tick it on its own thread and wait until it completes.
        if (server != null) {
            CountDownLatch latch = new CountDownLatch(1);
            server.execute(() -> {
                server.tickServer(()->false);
                latch.countDown();
            });
            try {
                // Block the main client thread until the server tick finishes.
                latch.await();
                //PlayerSync.LOGGER.info("After waiting for server to finish");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        ((BlockableEventLoopAccessor) mc).callRunAllTasks();
        mc.tick();

        return 1;
    }

    @ModifyVariable(
            method = "runTick(Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/Window;setErrorSection(Ljava/lang/String;)V",
                    shift = At.Shift.AFTER
            ),
            ordinal = 0,
            argsOnly = true)
    private boolean restoreRenderLevel(boolean renderLevel) {
        return this.cachedRenderLevel;
    }
}
