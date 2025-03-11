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
import net.minecraft.client.server.IntegratedServer;
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

    public enum TLMode {REC_COUNTDOWN, REC, PLAYBACK, NONE}
    private static TLMode currMode = TLMode.NONE;
    //private static boolean recording = false;
    //private static boolean playbackEnabled = false;
    private static boolean playbackPaused = false;
    private static boolean playbackDetatched = false;

    private static int frame = 0;
    private static int prevFrame = 0;
    public static void updatePrevFrame(){prevFrame = frame;}

    private static final int COUNTDOWN_DURATION_FRAMES = 3 * 60; // 3 seconds at 60 fps
    private static int countdownDurationFrames = COUNTDOWN_DURATION_FRAMES;
    private static int countdownStartFrame = 0;

    private static final List<SyncKeyframe> recordedKeyframes = new ArrayList<>();

    public static TLMode getMode() {return currMode;}
    //public static boolean isPlaybackEnabled() {return playbackEnabled; }
    //public static boolean isRecording(){return recording;}
    public static boolean isPlaybackPaused(){return playbackPaused; }
    public static boolean isPlaybackDetatched() {return playbackDetatched;}
    public static boolean isTickFrame() { return (getFrame() % 3) == 0;}
    public static boolean hasFrameChanged(){ return (getFrame() != getPrevFrame());}
    //public static boolean isLockstepMode() {return (/*isRecording() || */isPlaybackEnabled()); }

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
        String debugText = " deltaTick: " + client.getTimer().getGameTimeDeltaPartialTick(true);

        if (currMode == TLMode.REC) {
            debugText += " recFrame: " + getRecFrame();
        }
        if (currMode == TLMode.PLAYBACK) {
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
                15728880                                            // packed light coordinates (0 if default)
        );

        if (currMode == TLMode.REC_COUNTDOWN) {
            int framesElapsed = getFrame() - countdownStartFrame;
            int framesLeft = countdownDurationFrames - framesElapsed;
            float countdownSeconds = framesLeft / 60.0f;

            if (countdownSeconds <= 0) {
                // Countdown finished – switch to recording
                setCurrMode(TLMode.REC, true);
                debugText += " [Countdown finished -> Recording]";
            } else {
                int centerX = client.getWindow().getGuiScaledWidth() / 2;
                int centerY = client.getWindow().getGuiScaledHeight() / 2;
                int displaySeconds = (int) Math.ceil(countdownSeconds);

                client.font.drawInBatch(
                        "Recording in: " + displaySeconds,
                        centerX - 50,
                        centerY,
                        0xFF0000,  // text color (red)
                        false,     // dropShadow flag
                        poseStack.last().pose(),
                        event.getGuiGraphics().bufferSource(),
                        Font.DisplayMode.NORMAL,
                        0,
                        15728880
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

    public static void setCurrMode(TLMode mode, boolean releaseKeysIfNecessary){
        if(currMode == mode)
            return;

        if(mode == TLMode.REC_COUNTDOWN){
            int totalFrames = getRecordedKeyframes().size();
            if(totalFrames == 0) {
                SLOGGER.info("No frames recorded, starting recording immediately.");
                setCurrMode(TLMode.REC, true);
                return;
            }
            // Clamp countdown duration if fewer than 3 seconds (COUNTDOWN_DURATION_FRAMES) of frames exist
            countdownDurationFrames = Math.min(COUNTDOWN_DURATION_FRAMES, totalFrames);
            int targetStart = totalFrames - countdownDurationFrames;
            setFrame(targetStart);
            countdownStartFrame = targetStart;
            SLOGGER.info("Starting recording countdown with " + countdownDurationFrames +
                    " frames (" + (countdownDurationFrames / 60.0f) + " seconds) countdown!");
        }

        if(mode == TLMode.REC)
            setFrame(getRecFrame());

        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if(server != null)
            server.tickRateManager().setFrozen(mode == TLMode.PLAYBACK);

        if(mode != TLMode.PLAYBACK && releaseKeysIfNecessary)
            InputsManager.releaseAllKeys();

        currMode = mode;
    }


    public static void setPlaybackDetatched(boolean value){
        if(playbackDetatched == value)
            return;
        playbackDetatched = value;
        SLOGGER.info("Set playback detatched: " + value);
        if(playbackDetatched)
            InputsManager.releaseAllKeys();
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

        float tickDelta = client.getTimer().getGameTimeDeltaPartialTick(true);
        if(frame == 0){
            LOGGER.error("playheadIndex: " + frame + " tickDelta: " + tickDelta);
        }

        long frameNumber = getFrame();

        Camera cam = client.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        Quaternionf camRot = cam.rotation();

        camRot = new Quaternionf(camRot.x, camRot.y, camRot.z, camRot.w);

        // Create a merged keyframe with both keyboard and mouse inputs.
        SyncKeyframe keyframe = new SyncKeyframe(
                frameNumber,
                tickDelta,
                player.position(),//lerpedPlayerPos,
                player.getYRot(),
                player.getXRot(),
                player.getDeltaMovement(),
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

        //SLOGGER.info("done setting player from keyframe");
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
