package net.doodlechaos.playersync.mixin.accessor;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Invoker("onPress")
    void invokeOnPress(long windowPointer, int button, int action, int modifiers);

    @Invoker("onScroll")
    void invokeOnScroll(long windowPointer, double xOffset, double yOffset);

    @Invoker("onMove")
    void invokeOnMove(long windowPointer, double xpos, double ypos);
}
