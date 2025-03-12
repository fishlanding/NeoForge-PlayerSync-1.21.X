package net.doodlechaos.playersync.input.containers;

import net.doodlechaos.playersync.mixin.accessor.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;

public class MouseScrollEvent extends MyInputEvent {
    public final double deltaX;
    public final double deltaY;

    public MouseScrollEvent(double deltaX, double deltaY) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    @Override
    public String toLine() {
        return "MouseScrollEvent;horizontal=" + deltaX + ";vertical=" + deltaY;
    }

    @Override
    public void simulate(long window, Minecraft client) {
        ((MouseHandlerAccessor)client.mouseHandler).invokeOnScroll(window, deltaX, deltaY);
    }

    /**
     * Deserializes a MouseScrollEvent from its string representation.
     * Expected format: "MouseScrollEvent;horizontal=0.0;vertical=-1.0"
     */
    public static MouseScrollEvent fromLine(String line) {
        String[] parts = line.split(";");
        double horizontal = 0, vertical = 0;
        for (String part : parts) {
            if (part.startsWith("horizontal=")) {
                horizontal = Double.parseDouble(part.substring("horizontal=".length()));
            } else if (part.startsWith("vertical=")) {
                vertical = Double.parseDouble(part.substring("vertical=".length()));
            }
        }
        return new MouseScrollEvent(horizontal, vertical);
    }

}
