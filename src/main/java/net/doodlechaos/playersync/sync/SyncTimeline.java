package net.doodlechaos.playersync.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.doodlechaos.playersync.PlayerSync;
import net.doodlechaos.playersync.VideoRenderer;
import net.doodlechaos.playersync.input.InputsManager;
import net.doodlechaos.playersync.mixin.accessor.CameraAccessor;
import net.doodlechaos.playersync.utils.PlayerSyncFolderUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static com.mojang.text2speech.Narrator.LOGGER;
import static net.doodlechaos.playersync.PlayerSync.SLOGGER;

@EventBusSubscriber(modid = PlayerSync.MOD_ID)
public class SyncTimeline {

    private static boolean recording = false;
    private static boolean playbackEnabled = false;
    private static boolean playbackPaused = false;
    private static boolean playbackDetatched = false;

    private static int frame = 0;
    private static int prevFrame = 0;
    public static void updatePrevFrame(){prevFrame = frame;}

    private static final int COUNTDOWN_DURATION_FRAMES = 3 * 60; // 3 seconds at 60 fps
    private static boolean countdownActive = false;
    private static int countdownStartFrame = 0;

    private static final List<SyncKeyframe> recordedKeyframes = new ArrayList<>();

    public static boolean isPlaybackEnabled() {return playbackEnabled; }
    public static boolean isRecording(){return recording;}
    public static boolean isPlaybackPaused(){return playbackPaused; }
    public static boolean isPlaybackDetatched() {return playbackDetatched;}
    public static boolean isTickFrame() { return (getFrame() % 3) == 0;}
    public static boolean hasFrameChanged(){ return (getFrame() != getPrevFrame());}
    public static boolean isLockstepMode() {return (isRecording() || (isPlaybackEnabled() && !isPlaybackPaused())); }
    public static boolean isCountdownActive(){return countdownActive;}

    public static int getFrame(){return frame;}
    public static int getPrevFrame(){return prevFrame;}
    public static int getRecFrame(){ return getRecordedKeyframes().size();};

    public static float getTickDelta(){ return (getFrame() % 3) / 3.0f;}
    public static float getPlayheadTime() {return getFrame() / 60.0f; }
    public static List<SyncKeyframe> getRecordedKeyframes() {
        return recordedKeyframes;
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        PoseStack poseStack = event.getGuiGraphics().pose();

        // Build debug text string
        String debugText = "inPlaybackMode:" + playbackEnabled;
        if (isRecording()) {
            debugText += " recFrame: " + getRecFrame();
        }
        if (isPlaybackEnabled()) {
            debugText += " playbackPaused:" + playbackPaused + " frame: " + frame;
        }

        // Draw debug text at position (10, 20) with white color (0xFFFFFF)
        client.font.drawInBatch(
                debugText,
                10,
                20,
                0xFFFFFF,                           // text color
                false,                              // dropShadow flag
                poseStack.last().pose(),            // Matrix4f from the PoseStack
                event.getGuiGraphics().bufferSource(), // MultiBufferSource
                Font.DisplayMode.NORMAL,            // Display mode
                0,                                  // background color (0 if none)
                0                                   // packed light coordinates (0 if default)
        );

        if (isCountdownActive()) {
            // Compute elapsed frames and remaining frames in the countdown
            int framesElapsed = getFrame() - countdownStartFrame;
            int framesLeft = COUNTDOWN_DURATION_FRAMES - framesElapsed;
            // Convert frames to seconds (approximate, assuming 60 FPS)
            float countdownSeconds = framesLeft / 60.0f;

            if (countdownSeconds <= 0) {
                // Countdown finished – update state and switch to recording
                countdownActive = false;
                setPlaybackEnabled(false, false);
                setRecording(true);
                debugText += " [Countdown finished -> Recording]";
            } else {
                // Display a large countdown text at the center of the screen
                int centerX = client.getWindow().getGuiScaledWidth() / 2;
                int centerY = client.getWindow().getGuiScaledHeight() / 2;
                int displaySeconds = (int) Math.ceil(countdownSeconds);

                client.font.drawInBatch(
                        "Recording in: " + displaySeconds,
                        centerX - 50,
                        centerY,
                        0xFF0000,                 // text color (red)
                        false,                          // dropShadow flag
                        poseStack.last().pose(),        // Matrix4f from the PoseStack
                        event.getGuiGraphics().bufferSource(), // MultiBufferSource
                        Font.DisplayMode.NORMAL,        // Display mode
                        0,                              // background color (0 if none)
                        0                               // packed light coordinates (0 if default)
                );
            }
        }
    }


    public static void advanceFrames(int count){
        SLOGGER.info("advancing: " + count);
        int nextFrame = frame + count;
        if(nextFrame >= recordedKeyframes.size())
            nextFrame = recordedKeyframes.size();
        setFrame(nextFrame);
    }

    public static void backupFrames(int amount){
        SLOGGER.info("backup: " + amount);
        int nextFrame = frame - amount;
        if(nextFrame <= 0)
            nextFrame = 0;
        setFrame(nextFrame);
    }

    public static void setFrame(int value){
        if(frame == value)
            return;
        frame = value;
        SLOGGER.info("Set frame to: " + value);
        setPlaybackDetatched(false);
    }

    public static void setPlaybackEnabled(boolean value, boolean releaseKeysIfNecessary){
        if(playbackEnabled == value)
            return;

        playbackEnabled = value;

        if(!playbackEnabled && releaseKeysIfNecessary)
            InputsManager.releaseAllKeys();
    }

    public static void setRecording(boolean value)
    {
        recording = value;
        setFrame(getRecFrame());
    }
    public static void setPlaybackDetatched(boolean value){
        if(playbackDetatched == value)
            return;
        playbackDetatched = value;
        SLOGGER.info("Set playback detatched: " + value);
        if(playbackDetatched)
            InputsManager.releaseAllKeys();
    }

    public static void startRecordingCountdown(){
        // If fewer than 3 seconds of frames exist, we'll just start from the beginning
        int totalFrames = getRecordedKeyframes().size();
        int targetStart = Math.max(0, totalFrames - COUNTDOWN_DURATION_FRAMES);

        setFrame(targetStart);
        setPlaybackEnabled(true, false);
        setPlaybackPaused(false);

        countdownActive = true;
        countdownStartFrame = targetStart;
        SLOGGER.info("Starting recording countdown!");
    }

    public static void setPlaybackPaused(boolean value){
        playbackPaused = value;
    }

    public static SyncKeyframe getCurrKeyframe(){
        List<SyncKeyframe> frames = getRecordedKeyframes();

        if(frame < 0 || frame >= frames.size())
            return null;

        return frames.get(frame);
    }

    /**
     * Records a new keyframe that captures both player and input data.
     */
    public static void CreateKeyframe(){
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if(player == null) {
            LOGGER.error("No player to record keyframe");
            return;
        }

        float tickDelta = client.getTimer().getGameTimeDeltaPartialTick(false);
        if(frame == 0){
            LOGGER.error("playheadIndex: " + frame + " tickDelta: " + tickDelta);
        }

        Vec3 lerpedPlayerPos = player.getPosition(tickDelta);

        long frameNumber = getFrame();

        Camera cam = client.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        Quaternionf camRot = cam.rotation();

        camRot = new Quaternionf(camRot.x, camRot.y, camRot.z, camRot.w);

        // Create a merged keyframe with both keyboard and mouse inputs.
        SyncKeyframe keyframe = new SyncKeyframe(
                frameNumber,
                tickDelta,
                lerpedPlayerPos,
                player.getYRot(),
                player.getXRot(),
                player.getDeltaMovementLerped(tickDelta),
                camPos,
                camRot,
                new ArrayList<>(InputsManager.getRecordedInputsBuffer()),
                new ArrayList<>()
        );

        recordedKeyframes.add(keyframe);
        SLOGGER.info("Recorded keyframe");

        InputsManager.clearRecordedInputsBuffer();
    }

    public static void setPlayerFromKeyframe(SyncKeyframe keyframe){

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        Camera cam = client.gameRenderer.getMainCamera();

        if(player == null)
            return;

        if(frame >= getRecordedKeyframes().size()){
            playbackPaused = true;

            if(VideoRenderer.isRendering()){
                VideoRenderer.FinishRendering();
                player.sendSystemMessage(Component.literal("Rendering complete"));
            }

            return;
        }

        if(keyframe == null)
            return;

        player.setPos(keyframe.playerPos);
        player.setYRot(keyframe.playerYaw);
        player.setXRot(keyframe.playerPitch);
        player.setDeltaMovement(keyframe.playerVel);

        Vector3f euler = new Vector3f();
        keyframe.camRot.getEulerAnglesYXZ(euler);

        ((CameraAccessor)cam).invokeSetRotation(euler.y, euler.x, euler.z);
        ((CameraAccessor)cam).invokeSetPosition(keyframe.camPos);

        SLOGGER.info("done setting player from keyframe");
    }

    public static void clearRecordedKeyframes(){
        recordedKeyframes.clear();
    }
    public static void pruneKeyframesAfterPlayhead(){
        if (recordedKeyframes.size() <= frame + 1) {
            return;
        }
        recordedKeyframes.subList(frame + 1, recordedKeyframes.size()).clear();
    }

    public static void SaveRecToFile(String recName) {
        File recFile = new File(PlayerSyncFolderUtils.getPlayerSyncFolder(), recName);
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Vec3.class, new SyncKeyframe.Vec3Adapter())
                .registerTypeAdapter(Quaternionf.class, new SyncKeyframe.QuaternionfAdapter())
                .setPrettyPrinting()
                .create();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(recFile))) {
            String json = gson.toJson(recordedKeyframes);
            writer.write(json);
            LOGGER.info("Recording saved to file: " + recFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Error saving recording to file: " + recFile.getAbsolutePath(), e);
        }
    }

    public static void LoadRecFromFile(String recName) {
        File recFile = new File(PlayerSyncFolderUtils.getPlayerSyncFolder(), recName);
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Vec3.class, new SyncKeyframe.Vec3Adapter())
                .registerTypeAdapter(Quaternionf.class, new SyncKeyframe.QuaternionfAdapter())
                .create();
        try (BufferedReader reader = new BufferedReader(new FileReader(recFile))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String json = sb.toString();
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<SyncKeyframe>>() {}.getType();
            List<SyncKeyframe> loadedKeyframes = gson.fromJson(json, listType);
            recordedKeyframes.clear();
            recordedKeyframes.addAll(loadedKeyframes);
            LOGGER.info("Recording loaded from file: " + recFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Error loading recording from file: " + recFile.getAbsolutePath(), e);
        }
    }

}
