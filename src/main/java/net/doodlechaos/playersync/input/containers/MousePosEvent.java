package net.doodlechaos.playersync.input.containers;

import net.doodlechaos.playersync.mixin.accessor.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;

public class MousePosEvent extends MyInputEvent {
    public final double x;
    public final double y;

    public MousePosEvent(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toLine() {
        return "MousePosEvent;x=" + x + ";y=" + y;
    }

    @Override
    public void simulate(long window, Minecraft client) {
        ((MouseHandlerAccessor)client.mouseHandler).invokeOnMove(window, x, y);
    }

    /**
     * Deserializes a MousePosEvent from its string representation.
     * Expected format: "MousePosEvent;x=100.5;y=200.5"
     */
    public static MousePosEvent fromLine(String line) {
        String[] parts = line.split(";");
        double x = 0, y = 0;
        for (String part : parts) {
            if (part.startsWith("x=")) {
                x = Double.parseDouble(part.substring("x=".length()));
            } else if (part.startsWith("y=")) {
                y = Double.parseDouble(part.substring("y=".length()));
            }
        }
        return new MousePosEvent(x, y);
    }

}