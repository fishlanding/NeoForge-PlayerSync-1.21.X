package net.doodlechaos.playersync.input.containers;

import net.minecraft.client.Minecraft;

public abstract class MyInputEvent {

    public abstract String toLine();

    // Each event will know how to simulate itself.
    public abstract void simulate(long window, Minecraft client);

/**
     * Factory method to deserialize an input event from a single-line string.
     * Delegates to the correct subclass based on the type prefix.
     *
     * @param line The serialized input event.
     * @return The deserialized InputEvent.
     */
    public static MyInputEvent fromLine(String line) {
        if (line.startsWith("KeyboardEvent")) {
            return KeyboardEvent.fromLine(line);
        } else if (line.startsWith("MouseButtonEvent")) {
            //return MouseButtonEvent.fromLine(line);
        } else if (line.startsWith("MousePosEvent")) {
            //return MousePosEvent.fromLine(line);
        } else if (line.startsWith("MouseScrollEvent")) {
           // return MouseScrollEvent.fromLine(line);
        } else {
            throw new IllegalArgumentException("Unknown event type: " + line);
        }
        return null;
    }
}