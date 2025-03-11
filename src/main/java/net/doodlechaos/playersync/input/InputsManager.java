package net.doodlechaos.playersync.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import net.doodlechaos.playersync.PlayerSync;
import net.doodlechaos.playersync.input.containers.KeyboardEvent;
import net.doodlechaos.playersync.input.containers.MouseButtonEvent;
import net.doodlechaos.playersync.input.containers.MyInputEvent;
import net.doodlechaos.playersync.sync.SyncKeyframe;
import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.ServerPackCommand;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.jline.keymap.KeyMap;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

import static net.doodlechaos.playersync.PlayerSync.SLOGGER;


@EventBusSubscriber(modid = PlayerSync.MOD_ID)
public class InputsManager {

    private static final List<MyInputEvent> recordedInputsBuffer = new ArrayList<>();
    public static String mostRecentCommand;

    private static boolean wasPeriodKeyDown = false;
    private static boolean wasCommaKeyDown = false;

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        // Process the event and create your KeyboardEvent instance if needed
        KeyboardEvent keyEvent = new KeyboardEvent(event.getKey(), event.getScanCode(), event.getAction(), event.getModifiers());
        recordedInputsBuffer.add(keyEvent);
        SLOGGER.info("detected key input: " + keyEvent.toLine());

        if(Minecraft.getInstance().screen instanceof ChatScreen)
            return;

        if(SyncTimeline.isCountdownActive())
            return;

        //Detect single key presses for controls
        if(keyEvent.key == GLFW.GLFW_KEY_P && keyEvent.action == GLFW.GLFW_PRESS)
            SyncTimeline.setPlaybackEnabled(!SyncTimeline.isPlaybackEnabled(), true);

        if(keyEvent.key == GLFW.GLFW_KEY_R && keyEvent.action == GLFW.GLFW_PRESS) {
           // if(SyncTimeline.isRecording())
                SyncTimeline.setRecording(!SyncTimeline.isRecording());
           // else if(!SyncTimeline.isCountdownActive())
           //     SyncTimeline.startRecordingCountdown();
        }

        if(!SyncTimeline.isPlaybackEnabled())
            return;

        if(keyEvent.key == GLFW.GLFW_KEY_SPACE && keyEvent.action == GLFW.GLFW_PRESS)
            SyncTimeline.setPlaybackPaused(!SyncTimeline.isPlaybackPaused());//playbackPaused = !playbackPaused;

        if(keyEvent.key == GLFW.GLFW_KEY_C && keyEvent.action == GLFW.GLFW_PRESS){
            SyncKeyframe keyframe = SyncTimeline.getCurrKeyframe();
            if(keyframe != null){
                keyframe.addCommand(InputsManager.mostRecentCommand);
                Minecraft client = Minecraft.getInstance();
                if(client.player != null)
                    client.player.sendSystemMessage(Component.literal("Added [" + InputsManager.mostRecentCommand + "] to keyframe " + SyncTimeline.getFrame()));
            }
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        HandleControls();
    }

    public static void HandleControls(){
        long window = Minecraft.getInstance().getWindow().getWindow();

        if(SyncTimeline.isPlaybackEnabled())
            handlePlaybackOnlyControls(window);
    }

    @Unique
    private static void handlePlaybackOnlyControls(long window){

        if(Minecraft.getInstance().screen instanceof ChatScreen)
            return;

        if(SyncTimeline.isCountdownActive())
            return;

        boolean leftShift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;

        if(GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS)
            SyncTimeline.advanceFrames(leftShift ? 2 : 1);
        if(GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS)
            SyncTimeline.backupFrames(leftShift ? 2 : 1);

        if(isKeyPressed(window, GLFW.GLFW_KEY_DOWN))
            SyncTimeline.setFrame(0);

        if(isKeyPressed(window, GLFW.GLFW_KEY_UP)){
            int recordedFrames = SyncTimeline.getRecordedKeyframes().size();
            if(recordedFrames > 0)
                SyncTimeline.setFrame(recordedFrames - 1);
        }

        // Advance frame (Period key)
        boolean isPeriodKeyDown = isKeyPressed(window, GLFW.GLFW_KEY_PERIOD);
        if (isPeriodKeyDown && !wasPeriodKeyDown) {
            SyncTimeline.advanceFrames(leftShift ? 2 : 1);
            SLOGGER.info("Detected advance frame key press");
        }
        wasPeriodKeyDown = isPeriodKeyDown;

        // Backup frame (Comma key)
        boolean isCommaKeyDown = isKeyPressed(window, GLFW.GLFW_KEY_COMMA);
        if (isCommaKeyDown && !wasCommaKeyDown) {
            SyncTimeline.backupFrames(leftShift ? 2 : 1);
            SLOGGER.info("Detected backup frame key press");
        }
        wasCommaKeyDown = isCommaKeyDown;

        if(isKeyPressed(window, GLFW.GLFW_KEY_W)
                || isKeyPressed(window, GLFW.GLFW_KEY_A)
                || isKeyPressed(window, GLFW.GLFW_KEY_S)
                || isKeyPressed(window, GLFW.GLFW_KEY_D))
            SyncTimeline.setPlaybackDetatched(true);
    }

    @Unique
    private static boolean isKeyPressed(long window, int glfwKey){
        if(GLFW.glfwGetKey(window, glfwKey) == GLFW.GLFW_PRESS)
            return true;
        return false;
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event){
        MouseButtonEvent mouseButtonEvent = new MouseButtonEvent(event.getButton(), event.getAction(), event.getModifiers());
        recordedInputsBuffer.add(mouseButtonEvent);
        SLOGGER.info("detected mouse input: " + mouseButtonEvent.toLine());
    }

    public static List<MyInputEvent> getRecordedInputsBuffer(){
        return recordedInputsBuffer;
    }

    public static void clearRecordedInputsBuffer(){
        // Clear the recorded event lists so they don't accumulate events across frames.
        recordedInputsBuffer.clear();
    }

    public static void releaseAllKeys(){

    }

    public static void simulateInputsFromKeyframe(SyncKeyframe keyframe){

        if(keyframe == null)
            return;

        Minecraft client = Minecraft.getInstance();
        long window = client.getWindow().getWindow();

        for (MyInputEvent ie : keyframe.recordedInputEvents) {
            ie.simulate(window, client);
        }

        //Execute the commands stored in the keyframe
        for(String cmd : keyframe.cmds){
            if (cmd == null || cmd.isEmpty()) {
                continue; // Skip null or empty commands
            }
            ExecuteCommandAsPlayer(cmd);
        }
    }

    public static void ExecuteCommandAsPlayer(String command) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            SLOGGER.error("Minecraft server is not available.");
            return;
        }

        // Get the client player first.
        LocalPlayer clientPlayer = Minecraft.getInstance().player;
        if (clientPlayer == null) {
            SLOGGER.error("Client player is not available.");
            return;
        }

        // Now look up the corresponding server player using the player's UUID.
        ServerPlayer player = server.getPlayerList().getPlayer(clientPlayer.getUUID());
        if (player == null) {
            SLOGGER.error("No corresponding server player found.");
            return;
        }

        // Ensure the command does not start with a slash
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        // Capture the modified command in a final variable for use in the lambda
        final String commandToExecute = command;

        server.execute(() -> {
            try {
                CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
                var parsedCommand = dispatcher.parse(commandToExecute, player.createCommandSourceStack());
                dispatcher.execute(parsedCommand);
                SLOGGER.info("Command executed successfully");
            } catch (Exception e) {
                SLOGGER.error("Failed to execute command [" + commandToExecute + "]: " + e.toString());
            }
        });
    }

}
