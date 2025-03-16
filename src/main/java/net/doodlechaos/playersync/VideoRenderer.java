package net.doodlechaos.playersync;

import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class VideoRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("playersync.VideoRenderer");

    // ffmpeg process and its input stream (for writing raw frames)
    private static Process ffmpegProcess = null;
    private static OutputStream ffmpegInput = null;

    // Video parameters
    public static int preFrameWaitCount = 0;
    private static int videoWidth = 0;
    private static int videoHeight = 0;
    private static final int FRAME_RATE = 60; // Change as needed

    // Flag to indicate if recording is active
    private static boolean rendering = false;
    public static boolean isRendering(){
        return rendering;
    }

    /**
     * Starts an ffmpeg process to record the game video and add the music.
     * This method determines the current window size and starts an ffmpeg process
     * that expects raw RGBA frames via its standard input and also uses the specified audio file.
     */
    public static void StartRendering() {
        if (rendering) {
            LOGGER.warn("Recording is already active.");
            return;
        }
        rendering = true;
        SyncTimeline.setFrame(0);
        SyncTimeline.setCurrMode(SyncTimeline.TLMode.PLAYBACK, true);
        SyncTimeline.setPlaybackPaused(true);

        Minecraft client = Minecraft.getInstance();
        videoWidth = client.getWindow().getScreenWidth();
        videoHeight = client.getWindow().getScreenHeight();
        LOGGER.info("Recording started at resolution {}x{}", videoWidth, videoHeight);

        // Build the ffmpeg command.
        // The command tells ffmpeg to:
        //   - overwrite output (-y)
        //   - read raw RGBA video frames from stdin with the given size and frame rate
        //   - use the provided audio file (inputAudioPathOgg) as a second input
        //   - encode the video using libx264 with ultrafast preset and the audio using AAC
        //   - flip the video vertically and scale to even dimensions
        //   - stop encoding when the shortest input (video or audio) ends (-shortest)
        List<String> command = new ArrayList<>();
        command.add(Config.CONFIG.pathToFFMPEG.get());
        command.add("-y");

        // Video input configuration
        command.add("-f");
        command.add("rawvideo");
        command.add("-pixel_format");
        command.add("rgba");
        command.add("-video_size");
        command.add(videoWidth + "x" + videoHeight);
        command.add("-framerate");
        command.add(String.valueOf(FRAME_RATE));
        command.add("-i");
        command.add("-");  // Video input from stdin

        // Audio input configuration
        command.add("-i");
        command.add(Config.CONFIG.inputAudioPathOgg.get());

        // Video encoding options
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("ultrafast");
        command.add("-pix_fmt");
        command.add("yuv420p");

        // Audio encoding options (using AAC)
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("192k");

        // Filters: flip vertically and scale to nearest even numbers
        command.add("-vf");
        command.add("vflip,scale=trunc(iw/2)*2:trunc(ih/2)*2");

        // Ensure the output stops at the shortest input's end (video in this case)
        command.add("-shortest");

        // Output file path
        command.add(Config.CONFIG.outputVideoPath.get());

        ProcessBuilder pb = new ProcessBuilder(command);
        try {
            ffmpegProcess = pb.start();
            ffmpegInput = ffmpegProcess.getOutputStream();

            // Read and log any errors from ffmpeg
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(ffmpegProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOGGER.error("ffmpeg: " + line);
                    }
                } catch (IOException e) {
                    LOGGER.error("Error reading ffmpeg error stream", e);
                }
            }).start();

            LOGGER.info("ffmpeg process started successfully.");
        } catch (IOException e) {
            LOGGER.error("Failed to start ffmpeg process", e);
            rendering = false;
        }
    }

    /**
     * Captures the current game frame and writes it to the ffmpeg process.
     * This method reads pixel data from the OpenGL framebuffer.
     */
    public static void CaptureFrame() {
        if (!rendering || ffmpegInput == null) return;

        // Create a ByteBuffer to hold the frame data (RGBA: 4 bytes per pixel)
        ByteBuffer buffer = BufferUtils.createByteBuffer(videoWidth * videoHeight * 4);

        // Read pixels from the framebuffer.
        GL11.glReadBuffer(GL11.GL_FRONT);
        GL11.glReadPixels(0, 0, videoWidth, videoHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        // Write the raw pixel data to ffmpeg's input.
        byte[] frame = new byte[videoWidth * videoHeight * 4];
        buffer.get(frame);
        try {
            ffmpegInput.write(frame);
        } catch (IOException e) {
            LOGGER.error("Error writing frame to ffmpeg", e);
        }
    }

    /**
     * Finishes the recording by closing the ffmpeg input stream and waiting for the process to exit.
     */
    public static void FinishRendering() {
        if (!rendering) return;
        rendering = false;
        try {
            if (ffmpegInput != null) {
                ffmpegInput.flush();
                ffmpegInput.close();
            }
            if (ffmpegProcess != null) {
                int exitCode = ffmpegProcess.waitFor();
                LOGGER.info("ffmpeg process exited with code {}", exitCode);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Error finishing ffmpeg recording", e);
        } finally {
            ffmpegProcess = null;
            ffmpegInput = null;
        }
    }
}
