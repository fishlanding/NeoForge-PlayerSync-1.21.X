package net.doodlechaos.playersync;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;


public class InputsManager {

/*    public static String mostRecentCommand;

    private static final List<InputEvent> recordedInputsBuffer = new ArrayList<>();

    private static boolean wasRKeyDown = false;
    private static boolean wasCKeyDown = false;
    private static boolean wasSpaceKeyDown = false;
    private static boolean wasPKeyDown = false;
    private static boolean wasPeriodKeyDown = false;
    private static boolean wasCommaKeyDown = false;

    // Called from your mixin to record a mouse button event.
    public static void recordMouseButtonEvent(MouseButtonEvent event) {
        PlayerSync.LOGGER.info("Recorded mouse button event on frame: " + SyncTimeline.getFrame() + " " + event.action);
        recordedInputsBuffer.add(event);
    }

    // Called from your mixin to record a mouse scroll event.
    public static void recordMouseScrollEvent(MouseScrollEvent event) {
        recordedInputsBuffer.add(event);
    }

    // Called from your mixin to record a mouse position event.
    public static void recordMousePosEvent(MousePosEvent event) {
        //recordedInputsBuffer.add(event);
    }

    // Called from your mixin to record a keyboard event.
    public static void recordKeyboardEvent(KeyboardEvent event) {
        LOGGER.info("recorded keyboard event on frame: " + PlayerTimeline.getFrame());
        recordedInputsBuffer.add(event);
    }

    public static List<InputEvent> getRecordedInputsBuffer(){
        return recordedInputsBuffer;
    }

    public static void clearRecordedInputsBuffer(){
        // Clear the recorded event lists so they don't accumulate events across frames.
        recordedInputsBuffer.clear();
    }

    public static void checkPlaybackKeyboardControls() {

        if (MinecraftClient.getInstance().world == null || MinecraftClient.getInstance().player == null)
            return;

        if(MinecraftClient.getInstance().currentScreen instanceof ChatScreen)
            return;

        if(PlayerTimeline.isCountdownActive())
            return;

        long window = MinecraftClient.getInstance().getWindow().getHandle();

        // Toggle playback mode (P key)
        boolean isPKeyDown = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_P);
        if (isPKeyDown && !wasPKeyDown) {
            PlayerTimeline.setPlaybackEnabled(!PlayerTimeline.isPlaybackEnabled(), true);
            LOGGER.info("Detected toggle playback mode key press");
        }
        wasPKeyDown = isPKeyDown;

        // Toggle Recording (R key)
        boolean isRKeyDown = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_R);
        if (isRKeyDown && !wasRKeyDown) {
            if(PlayerTimeline.isRecording())
                PlayerTimeline.setRecording(false);
            else if(!PlayerTimeline.isCountdownActive())
                PlayerTimeline.startRecordingCountdown();
        }
        wasRKeyDown = isRKeyDown;

        if (!PlayerTimeline.isPlaybackEnabled())
            return;

        // Save most recent command to keyframe (C key)
        boolean isCKeyDown = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_C);
        if (isCKeyDown && !wasCKeyDown) {
            PlayerKeyframe keyframe = PlayerTimeline.getCurKeyframe();

            if(keyframe != null)
                PlayerTimeline.addCommandToKeyframe(InputsManager.mostRecentCommand, keyframe);
        }
        wasCKeyDown = isCKeyDown;

        if(InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT))
            PlayerTimeline.advanceFrames(InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT) ? 2 : 1);

        if(InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT))
            PlayerTimeline.backupFrames(InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT) ? 2 : 1);

        if(InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_DOWN))
            PlayerTimeline.setFrame(0);

        if(InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_UP)){
            int recordedFrames = PlayerTimeline.getRecordedKeyframes().size();
            if(recordedFrames > 0)
                PlayerTimeline.setFrame(recordedFrames - 1);
        }

        // Toggle playback paused (Space key)
        boolean isSpaceKeyDown = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_SPACE);
        if (isSpaceKeyDown && !wasSpaceKeyDown && !PlayerTimeline.isPlayerDetatched()) {
            PlayerTimeline.setPlaybackPaused(!PlayerTimeline.isPlaybackPaused());//playbackPaused = !playbackPaused;
            LOGGER.info("Detected toggle playback paused key press");
        }
        wasSpaceKeyDown = isSpaceKeyDown;

        // Advance frame (Period key)
        boolean isPeriodKeyDown = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_PERIOD);
        if (isPeriodKeyDown && !wasPeriodKeyDown) {
            PlayerTimeline.advanceFrames(1);
            LOGGER.info("Detected advance frame key press");
        }
        wasPeriodKeyDown = isPeriodKeyDown;

        // Backup frame (Comma key)
        boolean isCommaKeyDown = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_COMMA);
        if (isCommaKeyDown && !wasCommaKeyDown) {
            PlayerTimeline.backupFrames(1);
            LOGGER.info("Detected backup frame key press");
        }
        wasCommaKeyDown = isCommaKeyDown;

        if(InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_W)
                || InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_A)
                || InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_S)
                || InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_D))
            PlayerTimeline.setPlayerDetatched(true);
    }

    public static void releaseAllKeys(){

        MinecraftClient client = MinecraftClient.getInstance();
        Keyboard keyboard = client.keyboard; // Adjust based on how you access your Keyboard instance
        long window = client.getWindow().getHandle();

        for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++) {
            // Skip F3 to prevent the debug menu from toggling.
            if (key == GLFW.GLFW_KEY_F3)
                continue;

            if(InputUtil.isKeyPressed(window, key)) //<< Adding this?
                continue;

            int scancode = GLFW.glfwGetKeyScancode(key);
            if(scancode <= 0)
                continue;
            keyboard.onKey(window, key, scancode, GLFW.GLFW_RELEASE, 0);
        }
        LOGGER.info("Released all keys");
    }

    public static void SimulateInputsFromKeyframe(PlayerKeyframe keyframe){

        if(keyframe == null)
            return;

        MinecraftClient client = MinecraftClient.getInstance();
        long window = client.getWindow().getHandle();


        for (InputEvent ie : keyframe.recordedInputEvents) {
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
        IntegratedServer minecraftServer = MinecraftClient.getInstance().getServer();
        if (minecraftServer == null) {
            LOGGER.error("Minecraft server is not available.");
            return;
        }

        ServerPlayerEntity player = PlayerSync.GetServerPlayer();
        if (player == null) {
            LOGGER.error("No players are currently online.");
            return;
        }

        // Ensure the command does not start with a slash
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        // Capture the modified command in a final variable for use in the lambda
        final String commandToExecute = command;

        minecraftServer.execute(() -> {
            try {
                CommandDispatcher<ServerCommandSource> dispatcher = minecraftServer.getCommandManager().getDispatcher();
                var parsedCommand = dispatcher.parse(commandToExecute, player.getCommandSource());
                dispatcher.execute(parsedCommand);
                LOGGER.info("Command executed successfully");
            } catch (Exception e) {
                LOGGER.error("Failed to execute command [" + commandToExecute + "]: " + e.toString());
            }
        });
    }*/

}
