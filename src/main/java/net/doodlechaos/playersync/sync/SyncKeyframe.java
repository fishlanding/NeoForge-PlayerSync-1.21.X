package net.doodlechaos.playersync.sync;

import net.doodlechaos.playersync.input.containers.MyInputEvent;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.List;

public class SyncKeyframe {

    public final long frame;
    public final float tickDelta;
    public final Vec3 playerPos;
    public float playerYaw;
    public float playerPitch;
    public final Vec3 playerVel;

    public final Vec3 camPos;
    public final Quaternionf camRot;

    public final List<MyInputEvent> recordedInputEvents;

    public final List<String> cmds;

    public SyncKeyframe(long frame, float tickDelta, Vec3 playerPos, float playerYaw, float playerPitch, Vec3 playerVel,
                        Vec3 camPos, Quaternionf camRot, List<MyInputEvent> inputEvents, List<String> cmds) {
        this.frame = frame;
        this.tickDelta = tickDelta;
        this.playerPos = playerPos;
        this.playerYaw = playerYaw;
        this.playerPitch = playerPitch;
        this.playerVel = playerVel;
        this.camPos = camPos;
        this.camRot = camRot;
        this.recordedInputEvents = inputEvents;
        this.cmds = cmds;
    }

}
