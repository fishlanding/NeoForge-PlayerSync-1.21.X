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
    private static int videoWidth = 0;
    private static int videoHeight = 0;
    private static final int FRAME_RATE = 60; // Change as needed

    // Configurable settings (defaults provided)
    private static String ffmpegPath = "C:\\FFmpeg\\bin\\ffmpeg.exe"; // default assumes ffmpeg is in system PATH
    private static String outputFile = "C:\\Users\\marky\\Downloads\\testRenderPlayerSync.mp4";

    // Flag to indicate if recording is active
    private static boolean rendering = false;
    public static boolean isRendering(){
        return rendering;
    }

    /**
     * Loads the configuration file (if available) from config/playersync.properties.
     * Expected properties:
     *   ffmpegPath - the path to the ffmpeg executable.
     *   outputFile - the desired output filename.
     */
    private static void loadConfig() {
        File configFile = new File("config/playersync.properties");
        if (configFile.exists()) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                ffmpegPath = props.getProperty("ffmpegPath", ffmpegPath);
                outputFile = props.getProperty("outputFile", outputFile);
                LOGGER.info("Loaded ffmpegPath: {} and outputFile: {}", ffmpegPath, outputFile);
            } catch (IOException e) {
                LOGGER.error("Error loading config file", e);
            }
        } else {
            LOGGER.info("Config file not found. Using default ffmpegPath and outputFile.");
        }
    }

    /**
     * Starts an ffmpeg process to record the game video.
     * This method determines the current window size and starts an ffmpeg process
     * that expects raw RGBA frames via its standard input.
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

        loadConfig(); // load configuration from file

        //long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
        //GLFW.glfwSetWindowSize(windowHandle, 1920, 1080);  //If I set the window size here, will it be ready in time?

        Minecraft client = Minecraft.getInstance();
        videoWidth = client.getWindow().getScreenWidth(); //.getFramebufferWidth();
        videoHeight = client.getWindow().getScreenHeight(); //.getFramebufferHeight();
        LOGGER.info("Recording started at resolution {}x{}", videoWidth, videoHeight);

        // Build the ffmpeg command.
        // This command tells ffmpeg to:
        //   - overwrite output (-y)
        //   - expect rawvideo in RGBA format of the given size at FRAME_RATE fps from stdin
        //   - encode using libx264 with ultrafast preset
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-f");
        command.add("rawvideo");
        command.add("-pixel_format");
        command.add("rgba");
        command.add("-video_size");
        command.add(videoWidth + "x" + videoHeight);
        command.add("-framerate");
        command.add(String.valueOf(FRAME_RATE));
        command.add("-i");
        command.add("-");
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("ultrafast");
        command.add("-pix_fmt");
        command.add("yuv420p");

        //This filter will scale the width and height to the nearest even number. It also flips the video vertically
        command.add("-vf");
        command.add("vflip,scale=trunc(iw/2)*2:trunc(ih/2)*2");


        command.add(outputFile);

        ProcessBuilder pb = new ProcessBuilder(command);
        try {
            ffmpegProcess = pb.start();
            ffmpegInput = ffmpegProcess.getOutputStream();

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
        // Note: glReadPixels reads from the lower-left corner. Depending on your setup,
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
