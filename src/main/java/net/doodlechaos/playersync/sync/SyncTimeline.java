package net.doodlechaos.playersync.sync;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.mojang.text2speech.Narrator.LOGGER;

public class SyncTimeline {

    private static boolean recording = false;
    private static boolean playbackEnabled = false;
    private static boolean playbackPaused = false;

    private static int frame = 0;
    private static int prevFrame = 0;

    private static final List<SyncKeyframe> recordedKeyframes = new ArrayList<>();

    public static boolean isPlaybackEnabled() {return playbackEnabled; }
    public static int getFrame(){return frame;}

    public static float getTickDelta(){ return (getFrame() % 3) / 3.0f;}
    public static float getPlayheadTime() {return getFrame() / 60.0f; }

    public static void advanceFrames(int count){
        frame += count;
        if(frame >= recordedKeyframes.size())
            setFrame(recordedKeyframes.size());
    }

    public static void backupFrames(int amount){
        frame -= amount;
        if(frame <= 0)
            setFrame(0);
    }

    public static void setFrame(int value){
        if(frame == value)
            return;
        frame = value;
    }

    public static void setPlaybackEnabled(boolean enabled){
        playbackEnabled = enabled;
    }

    public static void testLockstep() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        ServerTickRateManager trm = server.tickRateManager();
        mc.tick();
        Thread currentThread = Thread.currentThread();
        System.out.println("Running on client thread: " + currentThread.getName());

        long gameTimeBefore = mc.level.getGameTime();
        LOGGER.info("gameTimeBeforeStep: " + gameTimeBefore);

        // Run the tick on the server thread and wait for it
        CompletableFuture<Void> tickFuture = CompletableFuture.runAsync(() -> {
            trm.stepGameIfPaused(1); // Step game if paused
            server.tickServer(() -> true); // Run a single server tick
        }, server::execute); // Execute on the server thread

        // Block the client thread until the server tick is done
        tickFuture.join(); // This waits for completion

        long gameTimeAfter = mc.level.getGameTime();
        LOGGER.info("gameTimeAfterStep: " + gameTimeAfter);
    }

}
