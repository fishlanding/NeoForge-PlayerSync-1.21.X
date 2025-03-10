package net.doodlechaos.playersync.mixin;

import net.doodlechaos.playersync.VideoRenderer;
import net.doodlechaos.playersync.mixin.accessor.BlockableEventLoopAccessor;
import net.doodlechaos.playersync.sync.SyncKeyframe;
import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CountDownLatch;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Unique
    private boolean cachedRenderLevel;

    //This is getting called at 60fps
    @Inject(method = "runTick", at = @At("HEAD"), cancellable = true)
    private void onStartRunTick(boolean renderLevel, CallbackInfo ci){

    }

    //TODO: I'd like to get this called at 60fps to be my main loop. I think it is! when I limit the FPS in game.
    @ModifyVariable(method = "runTick(Z)V", at = @At("HEAD"), argsOnly = true)
    private boolean modifyRenderLevel(boolean renderLevel) {
        if(!SyncTimeline.isLockstepMode())
            return renderLevel;

        // Cache and override the renderLevel parameter if tick lockstep is active
        cachedRenderLevel = renderLevel;
        return false;
    }

    @Redirect(
            method = "runTick(Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/DeltaTracker$Timer;advanceTime(JZ)I"
            )
    )
    private int redirectAdvanceTime(DeltaTracker.Timer timer, long timeMillis, boolean renderLevel) {

        if(SyncTimeline.isPlaybackEnabled() && !SyncTimeline.isPlaybackDetatched()){
            SyncKeyframe keyframe = SyncTimeline.getCurrKeyframe();
            SyncTimeline.setPlayerFromKeyframe(keyframe);
        }

        if (!SyncTimeline.isLockstepMode())
            return timer.advanceTime(timeMillis, renderLevel);

        SyncTimeline.updatePrevFrame();
        if(SyncTimeline.isRecording() ||
                (SyncTimeline.isPlaybackEnabled() && !SyncTimeline.isPlaybackPaused()))
            SyncTimeline.advanceFrames(1); //DO this here so that the server and client are on the same frame



        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();

        //TODO: Simulate inputs based on the keyframe

        //If we're recording, or if playback is playing, or if playback is paused and the frame has changed, tick the server+client
        if(SyncTimeline.isTickFrame() && SyncTimeline.hasFrameChanged()){
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
        }

        return -1;
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
        if(!SyncTimeline.isLockstepMode())
            return renderLevel;

        return this.cachedRenderLevel;
    }

    //At the end of runTick
    @Inject(method = "runTick", at = @At("TAIL"), cancellable = true)
    private void onEndRunTick(boolean renderLevel, CallbackInfo ci){
        if(SyncTimeline.isRecording()){
            SyncTimeline.CreateKeyframe();
        }

        if(VideoRenderer.isRendering()){
            VideoRenderer.CaptureFrame();
        }
    }
}
