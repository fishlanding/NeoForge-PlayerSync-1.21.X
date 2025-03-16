package net.doodlechaos.playersync.mixin;

import net.doodlechaos.playersync.VideoRenderer;
import net.doodlechaos.playersync.input.InputsManager;
import net.doodlechaos.playersync.sync.AudioSync;
import net.doodlechaos.playersync.sync.SyncKeyframe;
import net.doodlechaos.playersync.sync.SyncTimeline;
import net.doodlechaos.playersync.sync.SyncTimeline.TLMode;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.CountDownLatch;

import static net.doodlechaos.playersync.PlayerSync.SLOGGER;

@Mixin(Minecraft.class)
public class MinecraftMixin {


    @Redirect(
            method = "runTick(Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/DeltaTracker$Timer;advanceTime(JZ)I"
            )
    )
    private int redirectAdvanceTime(DeltaTracker.Timer timer, long timeMillis, boolean renderLevel) {

        if (!SyncTimeline.isSomeFormOfPlayback())
            return timer.advanceTime(timeMillis, renderLevel);

        if(SyncTimeline.isPlaybackDetached())
            return timer.advanceTime(timeMillis, renderLevel);

        timer.advanceTime(timeMillis, renderLevel); //Still advance the time, just don't use the tick value it returns. I must do this so when I go back to recording there isn't a sudden LURCH back to real time

        SyncKeyframe keyframe = SyncTimeline.getCurrKeyframe();
        SyncTimeline.setPlayerFromKeyframe(keyframe);

        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();

        int i = 0;
        boolean istickFrame = SyncTimeline.isTickFrame();
        boolean hasFrameChanged = SyncTimeline.hasFrameChanged();

        if(!hasFrameChanged)
            return 0;

        //Changing REAL timeline time
        //If playback is playing, or if playback is paused and the frame has changed, update in lockstep
        InputsManager.simulateInputsFromKeyframe(keyframe);

        if(istickFrame){
            // If the integrated server exists, tick it on its own thread and wait until it completes.
            if (server != null) {
                CountDownLatch latch = new CountDownLatch(1);
                server.tickRateManager().stepGameIfPaused(1);
                server.execute(() -> {
                    server.tickServer(()->false);
                    latch.countDown();
                });
                try {
                    // Block the main client thread until the server tick finishes.
                    latch.await();
                    SLOGGER.info("After waiting for server to finish");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            i = 1;
        }
        SyncTimeline.updatePrevFrame(); //IMPORTANT: Immediately after I'm checking if the frame is dirty, then I update the prevFrame. This way, the inputs can be read and change the currFrame without that info being lost
                                        //Updating the prevFrame must happen BEFORE I read the inputs from the player (which can modify the currFrame)

        return i;
    }

    @Unique
    private int renderFrameWaitCounter = 0;

    //At the end of runTick
    @Inject(method = "runTick", at = @At("TAIL"), cancellable = true)
    private void onEndRunTick(boolean renderLevel, CallbackInfo ci){
        if(SyncTimeline.getMode() == SyncTimeline.TLMode.REC){
            SyncTimeline.CreateKeyframe();
        }

        if(VideoRenderer.isRendering()){
            renderFrameWaitCounter++;
            if(renderFrameWaitCounter > VideoRenderer.preFrameWaitCount){
                renderFrameWaitCounter = 0;
                VideoRenderer.CaptureFrame();
                SyncTimeline.scrubFrames(1);
            }
        }

        if(SyncTimeline.getMode() == TLMode.REC_COUNTDOWN
                || (SyncTimeline.getMode() == TLMode.PLAYBACK && !SyncTimeline.isPlaybackPaused()))
            SyncTimeline.scrubFrames(1); //DO this here so that the server and client are on the same frame

        int nextFrameNum = SyncTimeline.getFrame() + SyncTimeline.framesToScrub;

        // If we are in REC_COUNTDOWN, check if the playhead has advanced beyond the last existing frame.
        // Because frames go from [0..size-1], "exactly 1 beyond existing" is 'size' itself.
        if (SyncTimeline.getMode() == SyncTimeline.TLMode.REC_COUNTDOWN) {
            if (nextFrameNum > SyncTimeline.getRecordedKeyframes().size()) {
                // Now we are exactly one beyond the final existing keyframe -> switch to REC
                SyncTimeline.setCurrMode(SyncTimeline.TLMode.REC, true);
                SLOGGER.info("Finished rec_countdown. Transitioning to recording on frame: " + SyncTimeline.getFrame());
            }
        }

        SyncTimeline.setFrame(nextFrameNum);
        SyncTimeline.framesToScrub = 0;

        AudioSync.updateAudio(SyncTimeline.getPlayheadTime());
    }
}
