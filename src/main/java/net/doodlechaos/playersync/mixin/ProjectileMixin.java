package net.doodlechaos.playersync.mixin;

import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Random;

import static net.doodlechaos.playersync.PlayerSync.SLOGGER;

/**
 * This mixin overrides getMovementToShoot to use deterministic randomness
 * based on the current frame number. It replaces the original call to
 * this.random.triangle(...) with a deterministic triangular distribution.
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    /**
     * Overwrites the original getMovementToShoot to create deterministic divergence.
     *
     * @param x         the x component of the direction vector
     * @param y         the y component of the direction vector
     * @param z         the z component of the direction vector
     * @param velocity  the velocity multiplier
     * @param inaccuracy the inaccuracy factor (divergence)
     * @return a Vec3 representing the final movement vector for the projectile
     * @author doodlechaos
     * @reason sdf
     */
    @Overwrite
    public Vec3 getMovementToShoot(double x, double y, double z, float velocity, float inaccuracy) {
        // Create a deterministic Random seeded with the current frame number.
        long frame = SyncTimeline.getFrame();
        Random deterministicRandom = new Random(frame);
        double scale = 0.0172275 * inaccuracy;

        // Emulate a triangular distribution by subtracting two uniform random values.
        double deltaX = (deterministicRandom.nextDouble() - deterministicRandom.nextDouble()) * scale;
        double deltaY = (deterministicRandom.nextDouble() - deterministicRandom.nextDouble()) * scale;
        double deltaZ = (deterministicRandom.nextDouble() - deterministicRandom.nextDouble()) * scale;

        // Normalize the input vector, add the deterministic divergence, and scale by velocity.
        return new Vec3(x, y, z).normalize().add(deltaX, deltaY, deltaZ).scale(velocity);
    }
}
