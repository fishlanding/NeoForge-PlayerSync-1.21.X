package net.doodlechaos.playersync.sync;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;
import static net.doodlechaos.playersync.PlayerSync.SLOGGER;
import static org.lwjgl.stb.STBVorbis.*;
import org.lwjgl.stb.STBVorbisInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AudioSync {

    // Constant for sample offset property (in seconds)
    private static final int AL_SEC_OFFSET = 0x1024;

    private static boolean loaded = false;

    // For timeline-sync
    private static float lastTimelineSeconds = 0.0f;
    private static final float OFFSET_TOLERANCE = 0.05f; // 50 ms
    private static long lastUpdateTimeNanos = -1;

    private static int forwardBufferId;
    private static int reverseBufferId;
    private static int forwardSourceId;
    private static int reverseSourceId;

    private static float audioLengthSeconds = 0.0f;

    // We’ll keep track of whether we’re using forward or reverse at the moment
    private static boolean usingForward = true;

    public static boolean loadAudio(String filePath) {
        cleanup(); // always cleanup prior buffers/sources if they exist

        ByteBuffer fileBuffer = null;
        try {
            // Read entire OGG file into memory
            byte[] bytes = Files.readAllBytes(Paths.get(filePath));
            fileBuffer = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();

            // Open with STB Vorbis
            IntBuffer errorBuffer = MemoryUtil.memAllocInt(1);
            long decoder = stb_vorbis_open_memory(fileBuffer, errorBuffer, null);
            if (decoder == MemoryUtil.NULL) {
                System.err.println("Failed to open OGG file: " + filePath
                        + " Error: " + errorBuffer.get(0));
                MemoryUtil.memFree(errorBuffer);
                return false;
            }

            // Gather info
            STBVorbisInfo info = STBVorbisInfo.malloc();
            stb_vorbis_get_info(decoder, info);
            int channels = info.channels();
            int sampleRate = info.sample_rate();
            int totalSamples = stb_vorbis_stream_length_in_samples(decoder);

            // We can also get the total length in seconds:
            float lengthInSeconds = stb_vorbis_stream_length_in_seconds(decoder);
            audioLengthSeconds = lengthInSeconds;

            // Decode PCM data
            ShortBuffer pcm = MemoryUtil.memAllocShort(totalSamples * channels);
            int samplesDecoded = stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
            pcm.limit(samplesDecoded * channels);

            stb_vorbis_close(decoder);
            MemoryUtil.memFree(errorBuffer);
            MemoryUtil.memFree(fileBuffer);
            info.free();

            // ------------------------------------------
            // 1) Create the forward buffer
            // ------------------------------------------
            forwardBufferId = AL10.alGenBuffers();
            int format = (channels == 1) ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            AL10.alBufferData(forwardBufferId, format, pcm, sampleRate);

            // ------------------------------------------
            // 2) Create the reversed buffer
            // ------------------------------------------
            // We'll create a new ShortBuffer with reversed contents
            ShortBuffer reversedPcm = MemoryUtil.memAllocShort(pcm.capacity());
            reversedPcm.limit(pcm.limit());

            // Reverse in terms of frames. For each frame, copy channels in order.
            int frameSize = channels; // e.g., 2 if stereo
            for (int i = pcm.limit() - frameSize; i >= 0; i -= frameSize) {
                for (int j = 0; j < frameSize; j++) {
                    reversedPcm.put(pcm.get(i + j));
                }
            }
            reversedPcm.flip();

            reverseBufferId = AL10.alGenBuffers();
            AL10.alBufferData(reverseBufferId, format, reversedPcm, sampleRate);

            // free up the PCM data
            MemoryUtil.memFree(pcm);
            MemoryUtil.memFree(reversedPcm);

            // ------------------------------------------
            // 3) Create two sources
            // ------------------------------------------
            forwardSourceId = AL10.alGenSources();
            AL10.alSourcei(forwardSourceId, AL10.AL_BUFFER, forwardBufferId);
            AL10.alSourcePlay(forwardSourceId); // start it playing by default

            reverseSourceId = AL10.alGenSources();
            AL10.alSourcei(reverseSourceId, AL10.AL_BUFFER, reverseBufferId);
            // We won’t play the reverse source until we need it, so leave it stopped for now

            loaded = true;
            SLOGGER.info("Loaded forward & reversed audio successfully (length = " + audioLengthSeconds + " seconds)");

            // Initialize timing
            lastUpdateTimeNanos = System.nanoTime();
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            if (fileBuffer != null) {
                MemoryUtil.memFree(fileBuffer);
            }
            return false;
        }
    }


    private static float prevMusicVolume = 0f;
    public static void updateAudio(float timelineSeconds) {
        if (!loaded) {
            return;
        }

        float musicVolume = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC);
        if(musicVolume != prevMusicVolume){
            AL10.alSourcef(forwardSourceId, AL10.AL_GAIN, musicVolume);
            AL10.alSourcef(reverseSourceId, AL10.AL_GAIN, musicVolume);
        }
        prevMusicVolume = musicVolume;

        // Calculate delta time automatically
        long currentTimeNanos = System.nanoTime();
        float deltaTime = (lastUpdateTimeNanos > 0)
                ? (currentTimeNanos - lastUpdateTimeNanos) / 1_000_000_000.0f
                : 0.0f;
        lastUpdateTimeNanos = currentTimeNanos;

        // 1) figure out speed from last timeline
        float timelineDelta = timelineSeconds - lastTimelineSeconds;
        float speed = 0.0f;
        if (deltaTime > 0.000001f) {
            speed = timelineDelta / deltaTime;
        }

        // We'll want the absolute value of pitch when we actually set AL_PITCH
        float pitch = Math.abs(speed);

        // 2) Decide if we need the forward or reverse source
        boolean shouldUseForward = (speed >= 0.0f);

        // If we are switching from forward to reverse or vice versa, we can do a quick offset sync
        if (shouldUseForward != usingForward) {
            // We are switching sources
            //  -> stop the old source
            if (usingForward) {
                AL10.alSourcePause(forwardSourceId);
            } else {
                AL10.alSourcePause(reverseSourceId);
            }

            //  -> set the offset on the new source
            // Forward offset is timelineSeconds
            // Reverse offset is (audioLengthSeconds - timelineSeconds)
            if (shouldUseForward) {
                float newOffset = timelineSeconds;
                // clamp between [0, audioLengthSeconds]
                newOffset = Math.max(0.0f, Math.min(newOffset, audioLengthSeconds));
                AL10.alSourcef(forwardSourceId, AL_SEC_OFFSET, newOffset);

                // resume forward source
                AL10.alSourcePlay(forwardSourceId);
            } else {
                // The reversed buffer is reversed in memory,
                // so if timelineSeconds=0 is actually the end of the forward buffer,
                // that means reversed offset = 0.0f is the end of the *original* wave.
                // So let's define reversedOffset:
                float newOffset = audioLengthSeconds - timelineSeconds;
                newOffset = Math.max(0.0f, Math.min(newOffset, audioLengthSeconds));
                AL10.alSourcef(reverseSourceId, AL_SEC_OFFSET, newOffset);

                // resume reverse source
                AL10.alSourcePlay(reverseSourceId);
            }

            usingForward = shouldUseForward;
        }

        // 3) Now set pitch on whichever source is active
        if (usingForward) {
            // ensure forward is playing
            int forwardState = AL10.alGetSourcei(forwardSourceId, AL10.AL_SOURCE_STATE);
            if (forwardState != AL10.AL_PLAYING) {
                AL10.alSourcePlay(forwardSourceId);
            }
            AL10.alSourcef(forwardSourceId, AL10.AL_PITCH, pitch);

        } else {
            // ensure reverse is playing
            int reverseState = AL10.alGetSourcei(reverseSourceId, AL10.AL_SOURCE_STATE);
            if (reverseState != AL10.AL_PLAYING) {
                AL10.alSourcePlay(reverseSourceId);
            }
            AL10.alSourcef(reverseSourceId, AL10.AL_PITCH, pitch);
        }

        // 4) Drift Correction: Ensure the audio offset is close to our target.
        if (usingForward) {
            // Target offset for forward source is the timelineSeconds value.
            float audioOffset = AL10.alGetSourcef(forwardSourceId, AL_SEC_OFFSET);
            float offsetError = timelineSeconds - audioOffset;
            if (Math.abs(offsetError) > OFFSET_TOLERANCE) {
                AL10.alSourcef(forwardSourceId, AL_SEC_OFFSET, timelineSeconds);
/*                LOGGER.info(String.format(
                        "Drift correction (forward): timeline=%.3f, audio=%.3f, error=%.3f",
                        timelineSeconds, audioOffset, offsetError));*/
            }
        } else {
            // Target offset for reverse source is audioLengthSeconds - timelineSeconds.
            float audioOffset = AL10.alGetSourcef(reverseSourceId, AL_SEC_OFFSET);
            float targetOffset = audioLengthSeconds - timelineSeconds;
            float offsetError = targetOffset - audioOffset;
            if (Math.abs(offsetError) > OFFSET_TOLERANCE) {
                AL10.alSourcef(reverseSourceId, AL_SEC_OFFSET, targetOffset);
                SLOGGER.info(String.format(
                        "Drift correction (reverse): timeline=%.3f, audio=%.3f, error=%.3f",
                        timelineSeconds, audioOffset, offsetError));
            }
        }

        // 5) Keep track of timeline for next frame
        lastTimelineSeconds = timelineSeconds;
    }


    public static void cleanup() {
        if (loaded) {
            AL10.alDeleteSources(forwardSourceId);
            AL10.alDeleteSources(reverseSourceId);
            AL10.alDeleteBuffers(forwardBufferId);
            AL10.alDeleteBuffers(reverseBufferId);
            loaded = false;
        }
    }

}
