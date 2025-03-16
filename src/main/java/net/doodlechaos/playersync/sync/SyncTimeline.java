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

    //private static final int COUNTDOWN_DURATION_FRAMES = 3 * 60; // 3 seconds at 60 fps
    public static int countdownDurationFramesTotal = 3 * 60;
    private static int countdownStartFrame = 0;
    private static int lastRecSessionStartIndex = -1;

    private static final List<SyncKeyframe> recordedKeyframes = new ArrayList<>();

    public static TLMode getMode() {return currMode;}
    public static boolean isPlaybackPaused(){return playbackPaused; }
    public static boolean isPlaybackDetached() {return playbackDetached;}
    public static boolean isTickFrame() { return (getFrame() % 3) == 0;}
    public static boolean hasFrameChanged(){ return (getFrame() != getPrevFrame());}
    public static boolean isSomeFormOfPlayback(){return currMode == TLMode.PLAYBACK || currMode == TLMode.REC_COUNTDOWN;}


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
    public static boolean allowTickServerFlag = false;

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        PoseStack poseStack = event.getGuiGraphics().pose();
        LocalPlayer player = client.player;

        // --- Camera / Euler angles ---
        Camera cam = client.gameRenderer.getMainCamera();
        Vector3f euler = new Vector3f();
        cam.rotation().getEulerAnglesYXZ(euler);

        float xDeg = euler.x * (180.0f / (float)Math.PI);
        float yDeg = euler.y * (180.0f / (float)Math.PI);
        float zDeg = euler.z * (180.0f / (float)Math.PI);

        // --- Current TLMode as a string ---
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

        // --- Build up the debug info using a StringBuilder ---
        StringBuilder debugBuilder = new StringBuilder();

        debugBuilder.append(String.format("Mode: %s\n", modeStr));
        if(PlayerSync.debugTextOverlay){
            debugBuilder.append(String.format("deltaTick: %.2f\n",
                    client.getTimer().getGameTimeDeltaPartialTick(true)))
                    .append(String.format("Euler (degrees): X=%.2f, Y=%.2f, Z=%.2f\n", xDeg, yDeg, zDeg));
        }


        // Additional lines for certain modes
        if (currMode == TLMode.REC) {
            debugBuilder.append(String.format("recFrame: %d\n", getRecFrame()));
        }
        if (currMode == TLMode.PLAYBACK) {
            debugBuilder.append(String.format(
                    "detached: %s | playbackPaused: %s | frame: %d\n",
                    playbackDetached, playbackPaused, frame
            ));
        }

        // --- If the player exists, show client position/movement and rotation ---
        if (player != null && PlayerSync.debugTextOverlay) {
            // Current and previous position
            debugBuilder.append(
                    String.format(
                            "Client Player Pos: Current [%.2f, %.2f, %.2f], Prev [%.2f, %.2f, %.2f]\n",
                            player.getX(), player.getY(), player.getZ(),
                            player.xo,     player.yo,     player.zo
                    )
            );
            // Current and previous rotation: getXRot() = pitch, getYRot() = yaw
            debugBuilder.append(
                    String.format(
                            "Client Player Rot: Current [pitch=%.2f, yaw=%.2f], Prev [pitch=%.2f, yaw=%.2f]\n",
                            player.getXRot(), player.getYRot(),
                            player.xRotO,     player.yRotO
                    )
            );

            // Delta movement if needed
            debugBuilder.append(String.format(
                    "Client Movement: X=%.2f, Y=%.2f, Z=%.2f\n",
                    player.getDeltaMovement().x,
                    player.getDeltaMovement().y,
                    player.getDeltaMovement().z
            ));

            // --- Also show the server player's position/rotation ---
            IntegratedServer server = client.getSingleplayerServer();
            if (server != null) {
                var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                if (serverPlayer != null) {
                    debugBuilder.append(
                            String.format(
                                    "Server Player Pos: Current [%.2f, %.2f, %.2f]",
                                    serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ()
                            )
                    );

                    // If you want server "previous" position too and it's accessible:
                    // serverPlayer.xo, serverPlayer.yo, serverPlayer.zo
                    debugBuilder.append(
                            String.format(
                                    ", Prev [%.2f, %.2f, %.2f]\n",
                                    serverPlayer.xo, serverPlayer.yo, serverPlayer.zo
                            )
                    );

                    debugBuilder.append(
                            String.format(
                                    "Server Player Rot: Current [pitch=%.2f, yaw=%.2f]",
                                    serverPlayer.getXRot(), serverPlayer.getYRot()
                            )
                    );
                    // If you have old rotation (xRotO, yRotO) on the server side:
                    debugBuilder.append(
                            String.format(
                                    ", Prev [pitch=%.2f, yaw=%.2f]\n",
                                    serverPlayer.xRotO, serverPlayer.yRotO
                            )
                    );

                    // Add server deltas if desired
                    debugBuilder.append(String.format(
                            "Server Movement: X=%.2f, Y=%.2f, Z=%.2f\n",
                            serverPlayer.getDeltaMovement().x,
                            serverPlayer.getDeltaMovement().y,
                            serverPlayer.getDeltaMovement().z
                    ));
                }
            }
        }

        // --- Turn that into a single string for drawing ---
        String finalDebugText = debugBuilder.toString();

        // --- Calculate max width for wrapping, then split and draw once ---
        int maxWidth = client.getWindow().getGuiScaledWidth() - 20; // 10px padding on each side
        int x = 10;
        int y = 20; // top padding

        List<FormattedCharSequence> debugLines =
                client.font.split(Component.literal(finalDebugText), maxWidth);

        for (FormattedCharSequence line : debugLines) {
            client.font.drawInBatch(
                    line,
                    x,
                    y,
                    0xFFFFFF,   // text color
                    false,      // no drop shadow
                    poseStack.last().pose(),
                    event.getGuiGraphics().bufferSource(),
                    Font.DisplayMode.NORMAL,
                    0,
                    15728880
            );
            y += client.font.lineHeight;
        }

        // --- Handle the separate countdown text in the center of the screen ---
        if (currMode == TLMode.REC_COUNTDOWN) {
            int framesElapsed = getFrame() - countdownStartFrame;
            int framesLeft = countdownDurationFramesTotal - framesElapsed;
            float countdownSeconds = framesLeft / 60.0f;

            // Remove the old transition here.
            // Just clamp the countdownSeconds to 0 if it goes negative:
            if (countdownSeconds < 0) {
                countdownSeconds = 0;
            }

            // Then continue drawing your "Recording in: X" text as before:
            int centerX = client.getWindow().getGuiScaledWidth() / 2;
            int centerY = client.getWindow().getGuiScaledHeight() / 2;
            int displaySeconds = (int) Math.ceil(countdownSeconds);

            client.font.drawInBatch(
                    "Recording in: " + displaySeconds,
                    centerX - 50,
                    centerY,
                    0xFF0000,
                    false,
                    poseStack.last().pose(),
                    event.getGuiGraphics().bufferSource(),
                    Font.DisplayMode.NORMAL,
                    0,
                    15728880
            );
        }

    }

    public static int framesToScrub = 0;
    public static void scrubFrames(int amount){ //Can be positive or negative
        framesToScrub += amount;
    }

    public static void setFrame(int value){
        if(frame == value)
            return;

        /////CLAMPING/////
        if(value < 0)
            value = 0;
        if(getMode() == TLMode.REC_COUNTDOWN && value >= recordedKeyframes.size()) //Allow the frame to be set one beyond if we're doing the countdown so we can detect for the transition to REC
            value = recordedKeyframes.size();
        if(getMode() == TLMode.PLAYBACK && value >= recordedKeyframes.size() - 1) //Use playback specifically here. Clamp to exact frames if in playback
            value = recordedKeyframes.size() - 1;
        /////////////////

        frame = value;
        SLOGGER.info("Set frame to: " + value);
        setPlaybackDetached(false);
    }

    /**
     * Sets the current timeline mode, handling transitions between modes.
     * When transitioning into REC from any other mode, note the start of the new session.
     */
    public static void setCurrMode(TLMode mode, boolean releaseKeysIfNecessary){
        if(currMode == mode) {
            return;
        }

        // If we are transitioning to REC (and were previously in a different mode),
        // record where the new recording session's keyframes will begin.
        if (currMode != TLMode.REC && mode == TLMode.REC) {
            lastRecSessionStartIndex = getRecordedKeyframes().size();
        }

        if(mode == TLMode.REC_COUNTDOWN){
            int totalFrames = getRecordedKeyframes().size();
            if(totalFrames == 0) {
                SLOGGER.info("No frames recorded, starting recording immediately.");
                setCurrMode(TLMode.REC, true);
            } else{
                countdownDurationFramesTotal = Math.min(countdownDurationFramesTotal, totalFrames);
                int targetStart = totalFrames - countdownDurationFramesTotal;
                setFrame(targetStart);
                countdownStartFrame = targetStart;
                SLOGGER.info("Starting recording countdown with " + countdownDurationFramesTotal +
                        " frames (" + (countdownDurationFramesTotal / 60.0f) + " seconds) countdown!");
            }

        }

        if(mode == TLMode.REC) {
            setFrame(getRecFrame());
        }

        currMode = mode; //ORDER IS VERY IMPORTANT HERE. isSomeFormOfPlayback has dependency below

        // Freeze/unfreeze the server if needed
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if(server != null) {
            server.tickRateManager().setFrozen(isSomeFormOfPlayback());
        }

        if(!isSomeFormOfPlayback() && releaseKeysIfNecessary) {
            InputsManager.releaseAllKeys();
        }
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
     * Removes all keyframes that were recorded during the most recent recording session,
     * i.e. from 'lastRecSessionStartIndex' up to the end of the timeline. Resets
     * lastRecSessionStartIndex so that only one undo operation is allowed.
     *
     * @return A string describing how many frames were removed and what the new timeline range is.
     */
    public static String undoLastRecSession() {
        // If we never recorded a session or haven't started a new one yet, do nothing.
        if (lastRecSessionStartIndex == -1) {
            return "No previous recording session to undo.";
        }

        // If the timeline size hasn't grown since we started last REC, nothing to remove.
        if (lastRecSessionStartIndex >= recordedKeyframes.size()) {
            lastRecSessionStartIndex = -1;
            return "No frames were recorded after the last session start; nothing to undo.";
        }

        int originalSize = recordedKeyframes.size();
        // Remove everything from lastRecSessionStartIndex to the end:
        recordedKeyframes.subList(lastRecSessionStartIndex, originalSize).clear();

        int framesRemoved = originalSize - lastRecSessionStartIndex;
        // Reset the session start index to indicate we've used our "undo."
        lastRecSessionStartIndex = -1;

        // Ensure the current playback frame is still valid.
        // If we ended up past the new end of the timeline, clamp it.
        if (frame >= recordedKeyframes.size()) {
            frame = recordedKeyframes.size() - 1;
            if (frame < 0) {
                frame = 0; // In case the timeline is now empty.
            }
        }

        // Build a nice status message.
        int newSize = recordedKeyframes.size();
        int startFrame = 0;
        int endFrame = newSize - 1;
        return "Removed " + framesRemoved + " frames from timeline. " +
                "New timeline range: " + startFrame + " - " + endFrame +
                " (size=" + newSize + ")";
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

        long frameNumber = recordedKeyframes.size(); //getFrame();

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

        setFrame((int)frameNumber);
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

        player.setYRot(keyframe.playerYaw);
        player.setXRot(keyframe.playerPitch);
        player.setDeltaMovement(keyframe.playerVel);

        Vector3f euler = new Vector3f();
        keyframe.camRot.getEulerAnglesYXZ(euler);
        keyframeCamEulerDegrees = new Vector3f(-(float)Math.toDegrees(euler.x), 180 -(float)Math.toDegrees(euler.y), -(float)Math.toDegrees(euler.z));

        ((CameraAccessor)cam).invokeSetRotation(keyframeCamEulerDegrees.y, keyframeCamEulerDegrees.x, keyframeCamEulerDegrees.z);
        ((CameraAccessor)cam).invokeSetPosition(keyframe.camPos);

        allowEntityMixinFlag = false;
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

    public static boolean LoadRecFromFile(String recName) {
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
            return true;
        } catch (IOException e) {
            LOGGER.error("Error loading recording from file: " + recFile.getAbsolutePath(), e);
            return false;
        }
    }

}
