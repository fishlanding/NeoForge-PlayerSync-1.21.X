package net.doodlechaos.playersync.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.doodlechaos.playersync.PlayerSync;
import net.doodlechaos.playersync.input.containers.*;
import net.doodlechaos.playersync.sync.SyncKeyframe;
import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

import static net.doodlechaos.playersync.PlayerSync.SLOGGER;
import static net.doodlechaos.playersync.sync.SyncTimeline.TLMode;

@EventBusSubscriber(modid = PlayerSync.MOD_ID)
public class InputsManager {

    private static final List<MyInputEvent> recordedInputsBuffer = new ArrayList<>();

    public static String mostRecentCommand;

    // Track single-press toggles
    private static boolean wasRKeyDown = false;
    private static boolean wasPKeyDown = false;
    private static boolean wasSpaceKeyDown = false;
    private static boolean wasCKeyDown = false;

    private static boolean wasPeriodKeyDown = false;
    private static boolean wasCommaKeyDown = false;

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        // If we are in record mode, record the raw key press in the buffer
        if (SyncTimeline.getMode() == TLMode.REC) {
            KeyboardEvent keyEvent = new KeyboardEvent(event.getKey(), event.getScanCode(), event.getAction(), event.getModifiers());
            addEventToBuffer(keyEvent);
            SLOGGER.info("Detected key input (record mode): " + keyEvent.toLine());
        }
    }

    @SubscribeEvent
    public static void onMouseButtonInput(InputEvent.MouseButton.Pre event) {
        if (SyncTimeline.getMode() == TLMode.REC) {
            MouseButtonEvent mouseButtonEvent = new MouseButtonEvent(event.getButton(), event.getAction(), event.getModifiers());
            addEventToBuffer(mouseButtonEvent);
        }
    }

    @SubscribeEvent
    public static void onMouseScrollInput(InputEvent.MouseScrollingEvent event) {
        if (SyncTimeline.getMode() == TLMode.REC) {
            MouseScrollEvent mouseScrollEvent = new MouseScrollEvent(event.getScrollDeltaX(), event.getScrollDeltaY());
            addEventToBuffer(mouseScrollEvent);
        }
    }


    public static void onMouseMove(long windowPointer, double xpos, double ypos) {
        if (SyncTimeline.getMode() == TLMode.REC) {
            // If you actually want to record mouse positions, uncomment the two lines below:
            // MousePosEvent mousePosEvent = new MousePosEvent(xpos, ypos);
            // addEventToBuffer(mousePosEvent);
        }
    }

    @SubscribeEvent
    public static void onPlayerCommand(ServerChatEvent event) {
        String rawText = event.getRawText();
        // If it starts with "/", store it as our "mostRecentCommand".
        if (rawText.startsWith("/")) {
            mostRecentCommand = rawText;
            SLOGGER.info("Captured command: " + rawText);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        // If the player is left-clicking with a Wooden Axe, store a WorldEdit-like "//pos1" command
        Player player = event.getEntity();
        if (player.getMainHandItem().getItem() == Items.WOODEN_AXE) {
            BlockPos pos = event.getPos();
            InputsManager.mostRecentCommand = "//pos1 " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // If the player is right-clicking with a Wooden Axe, store a WorldEdit-like "//pos2" command
        Player player = event.getEntity();
        if (player.getMainHandItem().getItem() == Items.WOODEN_AXE) {
            BlockPos pos = event.getPos();
            InputsManager.mostRecentCommand = "//pos2 " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        handleControls();
    }

    public static void handleControls() {
        Minecraft client = Minecraft.getInstance();
        // If we have a GUI open (ChatScreen excepted if you want?), you might skip or allow partial logic:
        // For this example, I'm skipping toggles if any screen is open *except* if you want Chat to block them:
        if (client.screen instanceof ChatScreen) {
            return;
        }

        long window = client.getWindow().getWindow();
        TLMode mode = SyncTimeline.getMode();

        //
        // Handle R key toggles for record
        //
        boolean rDown = isKeyPressed(window, GLFW.GLFW_KEY_R);
        if (rDown && !wasRKeyDown) {
            // single-press toggle
            if (mode == TLMode.REC || mode == TLMode.REC_COUNTDOWN) {
                // Turn off recording
                SyncTimeline.setCurrMode(TLMode.NONE, true);
            } else if (mode == TLMode.NONE) {
                // Turn on recording (with countdown)
                SyncTimeline.setCurrMode(TLMode.REC_COUNTDOWN, true);
            }
        }
        wasRKeyDown = rDown;

        //
        // Handle P key toggles for playback
        //
        boolean pDown = isKeyPressed(window, GLFW.GLFW_KEY_P);
        if (pDown && !wasPKeyDown) {
            if (mode == TLMode.PLAYBACK) {
                // Turn off playback
                SyncTimeline.setCurrMode(TLMode.NONE, true);
            } else {
                // Turn on playback
                SyncTimeline.setCurrMode(TLMode.PLAYBACK, true);
            }
        }
        wasPKeyDown = pDown;

        // If we are now in playback mode, poll more playback-specific controls:
        if (mode == TLMode.PLAYBACK) {
            handlePlaybackOnlyControls(window);
        }
    }

    @Unique
    private static void handlePlaybackOnlyControls(long window) {

        // SHIFT can be used to scrub by 2 frames
        boolean leftShift = (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS);


        // Pressing "C" adds the mostRecentCommand to the current keyframe
        boolean cDown = isKeyPressed(window, GLFW.GLFW_KEY_C);
        if (cDown && !wasCKeyDown) {
            SyncKeyframe keyframe = SyncTimeline.getCurrKeyframe();
            if (keyframe != null) {
                keyframe.addCommand(InputsManager.mostRecentCommand);
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal(
                            "Added [" + InputsManager.mostRecentCommand + "] to keyframe " + SyncTimeline.getFrame()));
                }
            }
        }
        wasCKeyDown = cDown;

        // Right arrow = go forward (1 or 2 frames if SHIFT)
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS) {
            SyncTimeline.scrubFrames(leftShift ? 2 : 1);
        }
        // Left arrow = go backward (1 or 2 frames if SHIFT)
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS) {
            SyncTimeline.scrubFrames(leftShift ? -2 : -1);
        }

        // Down arrow = jump to frame 0
        if (isKeyPressed(window, GLFW.GLFW_KEY_DOWN)) {
            SyncTimeline.setFrame(0);
        }
        // Up arrow = jump to the last recorded keyframe
        if (isKeyPressed(window, GLFW.GLFW_KEY_UP)) {
            int recordedFrames = SyncTimeline.getRecordedKeyframes().size();
            if (recordedFrames > 0) {
                SyncTimeline.setFrame(recordedFrames - 1);
            }
        }

        // Period/comma = single-step forward/back
        boolean periodDown = isKeyPressed(window, GLFW.GLFW_KEY_PERIOD);
        if (periodDown && !wasPeriodKeyDown) {
            SyncTimeline.scrubFrames(leftShift ? 2 : 1);
            SLOGGER.info("Detected advance frame key press");
        }
        wasPeriodKeyDown = periodDown;

        boolean commaDown = isKeyPressed(window, GLFW.GLFW_KEY_COMMA);
        if (commaDown && !wasCommaKeyDown) {
            SyncTimeline.scrubFrames(leftShift ? -2 : -1);
            SLOGGER.info("Detected backup frame key press");
        }
        wasCommaKeyDown = commaDown;

        // If user manually presses WASD, we "detach" from the timeline
        if (isKeyPressed(window, GLFW.GLFW_KEY_W)
                || isKeyPressed(window, GLFW.GLFW_KEY_A)
                || isKeyPressed(window, GLFW.GLFW_KEY_S)
                || isKeyPressed(window, GLFW.GLFW_KEY_D)) {
            SyncTimeline.setPlaybackDetatched(true);
        }

        if(!SyncTimeline.isPlaybackDetatched()){ //I use space to move around while detatched, so don't allow it to toggle the playback
            // SPACE toggles playback paused
            boolean spaceDown = isKeyPressed(window, GLFW.GLFW_KEY_SPACE);
            if (spaceDown && !wasSpaceKeyDown) {
                SyncTimeline.setPlaybackPaused(!SyncTimeline.isPlaybackPaused());
            }
            wasSpaceKeyDown = spaceDown;
        }
    }

    @Unique
    private static boolean isKeyPressed(long window, int glfwKey) {
        return (GLFW.glfwGetKey(window, glfwKey) == GLFW.GLFW_PRESS);
    }

    private static void addEventToBuffer(MyInputEvent myInputEvent) {
        recordedInputsBuffer.add(myInputEvent);
        SLOGGER.info("Recorded input event on frame " + SyncTimeline.getFrame() + ": " + myInputEvent.getClass().getSimpleName());
    }

    public static List<MyInputEvent> getRecordedInputsBuffer() {
        return recordedInputsBuffer;
    }

    public static void clearRecordedInputsBuffer() {
        recordedInputsBuffer.clear();
    }


    public static void releaseAllKeys() {
        Minecraft client = Minecraft.getInstance();
        KeyboardHandler keyboard = client.keyboardHandler; // Adjust based on how you access your Keyboard instance
        long window = client.getWindow().getWindow();

        for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++) {
            // Skip F3 to prevent the debug menu from toggling.
            if (key == GLFW.GLFW_KEY_F3)
                continue;

            if(isKeyPressed(window, key))
                continue;

            int scancode = GLFW.glfwGetKeyScancode(key);
            if(scancode <= 0)
                continue;
            keyboard.keyPress(window, key, scancode, GLFW.GLFW_RELEASE, 0);
        }
        SLOGGER.info("Released all keys");
    }

    public static void simulateInputsFromKeyframe(SyncKeyframe keyframe) {
        if (keyframe == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        long window = client.getWindow().getWindow();

        // Simulate each buffered input event
        for (MyInputEvent ie : keyframe.recordedInputEvents) {
            ie.simulate(window, client);
            PlayerSync.SLOGGER.info("Simulated event on frame " + SyncTimeline.getFrame() + ": " + ie.getClass().getSimpleName());
        }

        // Execute any commands attached to the keyframe
        for (String cmd : keyframe.cmds) {
            if (cmd != null && !cmd.isEmpty()) {
                ExecuteCommandAsPlayer(cmd);
            }
        }
    }

    public static void ExecuteCommandAsPlayer(String command) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            SLOGGER.error("Minecraft server is not available.");
            return;
        }

        LocalPlayer clientPlayer = Minecraft.getInstance().player;
        if (clientPlayer == null) {
            SLOGGER.error("Client player is not available.");
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(clientPlayer.getUUID());
        if (player == null) {
            SLOGGER.error("No corresponding server player found.");
            return;
        }

        // Strip leading slash if present
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        final String commandToExecute = command;
        server.execute(() -> {
            try {
                var dispatcher = server.getCommands().getDispatcher();
                var parsedCommand = dispatcher.parse(commandToExecute, player.createCommandSourceStack());
                dispatcher.execute(parsedCommand);
                SLOGGER.info("Command executed successfully: " + commandToExecute);
            } catch (Exception e) {
                SLOGGER.error("Failed to execute command [" + commandToExecute + "]: " + e);
            }
        });
    }
}
