package net.doodlechaos.playersync.input.containers;

import net.doodlechaos.playersync.PlayerSync;
import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.client.Minecraft;

public class KeyboardEvent extends MyInputEvent {
    public final int key;
    public final int scancode;
    public final int action;
    public final int modifiers;

    public KeyboardEvent(int key, int scancode, int action, int modifiers) {
        this.key = key;
        this.scancode = scancode;
        this.action = action;
        this.modifiers = modifiers;
    }

    @Override
    public String toLine() {
        return "KeyboardEvent;key=" + key + ";scancode=" + scancode + ";action=" + action + ";modifiers=" + modifiers;
    }

    @Override
    public void simulate(long window, Minecraft client) {
        client.keyboardHandler.keyPress(window, key, scancode, action, modifiers);
        PlayerSync.SLOGGER.info("Simulated keyboard event on frame: " + SyncTimeline.getFrame() + " key " + key + ", action " + action);
    }

    /**
     * Deserializes a KeyboardEvent from its string representation.
     * Expected format: "KeyboardEvent;key=65;scancode=30;action=1;modifiers=0"
     */
    public static KeyboardEvent fromLine(String line) {
        String[] parts = line.split(";");
        int key = 0, scancode = 0, action = 0, modifiers = 0;
        for (String part : parts) {
            if (part.startsWith("key=")) {
                key = Integer.parseInt(part.substring("key=".length()));
            } else if (part.startsWith("scancode=")) {
                scancode = Integer.parseInt(part.substring("scancode=".length()));
            } else if (part.startsWith("action=")) {
                action = Integer.parseInt(part.substring("action=".length()));
            } else if (part.startsWith("modifiers=")) {
                modifiers = Integer.parseInt(part.substring("modifiers=".length()));
            }
        }
        return new KeyboardEvent(key, scancode, action, modifiers);
    }

}
