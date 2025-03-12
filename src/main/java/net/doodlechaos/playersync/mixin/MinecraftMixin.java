package net.doodlechaos.playersync.mixin;

import net.doodlechaos.playersync.VideoRenderer;
import net.doodlechaos.playersync.sync.SyncKeyframe;
import net.doodlechaos.playersync.sync.SyncTimeline;
import net.doodlechaos.playersync.sync.SyncTimeline.TLMode;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
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

        if (SyncTimeline.getMode() != TLMode.PLAYBACK)
            return timer.advanceTime(timeMillis, renderLevel);

        SyncKeyframe keyframe = SyncTimeline.getCurrKeyframe();

        if(!SyncTimeline.isPlaybackDetatched())
            SyncTimeline.setPlayerFromKeyframe(keyframe);

        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();

        //InputsManager.simulateInputsFromKeyframe(keyframe);
        int i = 0;
        //If we're recording, or if playback is playing, or if playback is paused and the frame has changed, tick the server+client
        boolean istickFrame = SyncTimeline.isTickFrame();
        boolean hasFrameChanged = SyncTimeline.hasFrameChanged();
        if(istickFrame && hasFrameChanged){
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
        SyncTimeline.updatePrevFrame(); //IMPORTANT: Immeidately after I'm checking if the frame is dirty, then I update the prevFrame. This way, the inputs can be read and change the currFrame without that info being lost
        //Updating the prevFrame must happen BEFORE I read the inputs from the player (which can modify the currFrame)

        return i;
    }

    //At the end of runTick
    @Inject(method = "runTick", at = @At("TAIL"), cancellable = true)
    private void onEndRunTick(boolean renderLevel, CallbackInfo ci){
        if(SyncTimeline.getMode() == SyncTimeline.TLMode.REC){
            SyncTimeline.CreateKeyframe();
        }

        if(VideoRenderer.isRendering()){
            VideoRenderer.CaptureFrame();
        }

        if(SyncTimeline.getMode() == TLMode.REC
                || SyncTimeline.getMode() == TLMode.REC_COUNTDOWN
                || (SyncTimeline.getMode() == TLMode.PLAYBACK && !SyncTimeline.isPlaybackPaused()))
            SyncTimeline.scrubFrames(1); //DO this here so that the server and client are on the same frame

        SyncTimeline.setFrame(SyncTimeline.getFrame() + SyncTimeline.framesToScrub);
        SyncTimeline.framesToScrub = 0;
    }
}
