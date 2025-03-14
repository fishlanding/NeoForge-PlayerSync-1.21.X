package net.doodlechaos.playersync.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.doodlechaos.playersync.PlayerSync;
import net.doodlechaos.playersync.VideoRenderer;
import net.doodlechaos.playersync.input.InputsManager;
import net.doodlechaos.playersync.input.containers.MyInputEvent;
import net.doodlechaos.playersync.mixin.accessor.CameraAccessor;
import net.doodlechaos.playersync.utils.PlayerSyncFolderUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
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
    private static boolean playbackPaused = false;
    private static boolean playbackDetached = false;

    private static int frame = 0;
    private static int prevFrame = 0;
    public static void updatePrevFrame(){prevFrame = frame;}

    private static final int COUNTDOWN_DURATION_FRAMES = 3 * 60; // 3 seconds at 60 fps
    private static int countdownDurationFrames = COUNTDOWN_DURATION_FRAMES;
    private static int countdownStartFrame = 0;

    private static final List<SyncKeyframe> recordedKeyframes = new ArrayList<>();

    public static TLMode getMode() {return currMode;}
    public static boolean isPlaybackPaused(){return playbackPaused; }
    public static boolean isPlaybackDetached() {return playbackDetached;}
    public static boolean isTickFrame() { return (getFrame() % 3) == 0;}
    public static boolean hasFrameChanged(){ return (getFrame() != getPrevFrame());}

    public static int getFrame(){return frame;}
    public static int getPrevFrame(){return prevFrame;}
    public static int getRecFrame(){ return getRecordedKeyframes().size();};

    public static float getTickDelta(){ return (getFrame() % 3) / 3.0f;}
    public static float getPlayheadTime() {return getFrame() / 60.0f; }
    public static List<SyncKeyframe> getRecordedKeyframes() {
        return recordedKeyframes;
    }

    public static Vector3f keyframeCamEulerDegrees = new Vector3f();
    public static boolean allowEntityMixinFlag = false;

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        PoseStack poseStack = event.getGuiGraphics().pose();
        LocalPlayer player = client.player;

        // Get the main camera and its Euler angles (in radians)
        Camera cam = client.gameRenderer.getMainCamera();
        Vector3f euler = new Vector3f();
        cam.rotation().getEulerAnglesYXZ(euler);

        // Convert the Euler angles from radians to degrees
        float xDeg = euler.x * (180.0f / (float)Math.PI);
        float yDeg = euler.y * (180.0f / (float)Math.PI);
        float zDeg = euler.z * (180.0f / (float)Math.PI);

        // Define a mode string for a consistent layout (adjust as needed)
        String modeStr;
        switch (currMode) {
            case REC:
                modeStr = "REC";
                break;
            case PLAYBACK:
                modeStr = "PLAYBACK";
                break;
            case REC_COUNTDOWN:
                modeStr = "COUNTDOWN";
                break;
            default:
                modeStr = "NONE";
                break;
        }

        // Build the base debug text with fixed-width fields
        String debugText = String.format(
                "Mode: %-9s | deltaTick: %6.2f | Euler: [x: %6.2f°, y: %6.2f°, z: %6.2f°]",
                modeStr,
                client.getTimer().getGameTimeDeltaPartialTick(true),
                xDeg, yDeg, zDeg
        );

        // Append additional information based on the mode
        if (currMode == TLMode.REC) {
            debugText += String.format(" | recFrame: %6d", getRecFrame());
        }
        if (currMode == TLMode.PLAYBACK) {
            debugText += String.format(" | detached: %-6s | playbackPaused: %-6s | frame: %6d",
                    playbackDetached, playbackPaused, frame);
        }

        // Calculate maximum width for wrapping (with padding)
        int maxWidth = client.getWindow().getGuiScaledWidth() - 20; // 10px padding on each side
        int y = 20; // starting y coordinate

        List<FormattedCharSequence> debugLines = client.font.split(Component.literal(debugText), maxWidth);
        for (FormattedCharSequence line : debugLines) {
            client.font.drawInBatch(
                    line,
                    10,         // x coordinate
                    y,          // y coordinate
                    0xFFFFFF,   // text color (white)
                    false,      // no drop shadow
                    poseStack.last().pose(),
                    event.getGuiGraphics().bufferSource(),
                    Font.DisplayMode.NORMAL,
                    0,          // no background color
                    15728880   // packed light coordinates
            );
            y += client.font.lineHeight;
        }

        // If the player exists, wrap and draw the player's position debug text below the main text block.
        if (player != null) {
            String playerPosDebug = String.format(
                    "Player: old pos [x: %6.2f, y: %6.2f, z: %6.2f] | curr pos [x: %6.2f, y: %6.2f, z: %6.2f]",
                    player.xo, player.yo, player.zo,
                    player.getX(), player.getY(), player.getZ()
            );
            List<FormattedCharSequence> playerLines = client.font.split(Component.literal(playerPosDebug), maxWidth);
            for (FormattedCharSequence line : playerLines) {
                client.font.drawInBatch(
                        line,
                        10,
                        y,
                        0xFFFFFF,
                        false,
                        poseStack.last().pose(),
                        event.getGuiGraphics().bufferSource(),
                        Font.DisplayMode.NORMAL,
                        0,
                        15728880
                );
                y += client.font.lineHeight;
            }

            // --- Upgrade: Also show the server player's position ---
            IntegratedServer server = client.getSingleplayerServer();
            if (server != null) {
                // Retrieve the server-side player using the client's player UUID.
                // Note: This requires that your mod environment exposes the server player via getPlayer.
                var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                if (serverPlayer != null) {
                    String serverPosDebug = String.format(
                            "Server Player: pos [x: %6.2f, y: %6.2f, z: %6.2f]",
                            serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ()
                    );
                    List<FormattedCharSequence> serverLines = client.font.split(Component.literal(serverPosDebug), maxWidth);
                    for (FormattedCharSequence line : serverLines) {
                        client.font.drawInBatch(
                                line,
                                10,
                                y,
                                0xFFFFFF,
                                false,
                                poseStack.last().pose(),
                                event.getGuiGraphics().bufferSource(),
                                Font.DisplayMode.NORMAL,
                                0,
                                15728880
                        );
                        y += client.font.lineHeight;
                    }
                }
            }
        }

        // Handle recording countdown overlay separately
        if (currMode == TLMode.REC_COUNTDOWN) {
            int framesElapsed = getFrame() - countdownStartFrame;
            int framesLeft = countdownDurationFrames - framesElapsed;
            float countdownSeconds = framesLeft / 60.0f;

            if (countdownSeconds <= 0) {
                // Countdown finished – switch to recording
                setCurrMode(TLMode.REC, true);
            } else {
                int centerX = client.getWindow().getGuiScaledWidth() / 2;
                int centerY = client.getWindow().getGuiScaledHeight() / 2;
                int displaySeconds = (int) Math.ceil(countdownSeconds);

                client.font.drawInBatch(
                        "Recording in: " + displaySeconds,
                        centerX - 50,
                        centerY,
                        0xFF0000,  // red text color
                        false,
                        poseStack.last().pose(),
                        event.getGuiGraphics().bufferSource(),
                        Font.DisplayMode.NORMAL,
                        0,
                        15728880
                );
            }
        }
    }


    public static int framesToScrub = 0;
    public static void scrubFrames(int amount){ //Can be positive or negative
        framesToScrub += amount;
    }

    public static void setFrame(int value){
        if(frame == value)
            return;

        if(value < 0) //Clamp within valid range
            value = 0;
        if(value >= recordedKeyframes.size())
            value = recordedKeyframes.size();

        frame = value;
        SLOGGER.info("Set frame to: " + value);
        setPlaybackDetached(false);
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

    public static void setPlaybackDetached(boolean value){
        if(playbackDetached == value)
            return;
        playbackDetached = value;
        SLOGGER.info("Set playback detatched: " + value);
        if(playbackDetached)
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

        float tickDelta = client.getTimer().getGameTimeDeltaPartialTick(true); //We're not in lockstep while recording, so this tickdelta will be "real"
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
                player.getPosition(tickDelta), //Use the "real" tickdelta
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

        //My objective is to only allow the player position and old positions to be set here during playback
        allowEntityMixinFlag = true;
        player.setOldPosAndRot(); //<< this prevents the deltaTick from affecting the player position (we're recording the position at 60fps, so no need for interpolation
        player.setPos(keyframe.playerPos);
        allowEntityMixinFlag = false;


        player.setYRot(keyframe.playerYaw);
        player.setXRot(keyframe.playerPitch);
        player.setDeltaMovement(keyframe.playerVel);

        Vector3f euler = new Vector3f();
        keyframe.camRot.getEulerAnglesYXZ(euler);
        keyframeCamEulerDegrees = new Vector3f(-(float)Math.toDegrees(euler.x), 180 -(float)Math.toDegrees(euler.y), -(float)Math.toDegrees(euler.z));

        ((CameraAccessor)cam).invokeSetRotation(keyframeCamEulerDegrees.y, keyframeCamEulerDegrees.x, keyframeCamEulerDegrees.z);
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
                .registerTypeAdapter(MyInputEvent.class, new SyncKeyframe.MyInputEventAdapter())
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
                .registerTypeAdapter(MyInputEvent.class, new SyncKeyframe.MyInputEventAdapter())
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
