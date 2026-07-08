package com.peek.utils.compat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

#if MC_VER <= 1212
import org.joml.Vector3f;
#endif

/**
 * Compatibility layer for particle spawning across different Minecraft versions.
 * Handles API differences between 1.21.2 and newer versions.
 */
public class ParticleCompat {
    
    /**
     * Spawns particles using the appropriate method signature for each version.
     * 
     * @param world The server world
     * @param player The target player
     * @param particle The particle effect to spawn
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param count Number of particles
     * @param velocityX X velocity
     * @param velocityY Y velocity
     * @param velocityZ Z velocity
     * @param speed Particle speed modifier
     */
    public static void spawnParticles(ServerLevel world, ServerPlayer player, ParticleOptions particle,
                                    double x, double y, double z, int count, 
                                    double velocityX, double velocityY, double velocityZ, double speed) {
        #if MC_VER >= 1219
        world.sendParticles(player, particle, false, false, x, y, z, count, velocityX, velocityY, velocityZ, speed);
        #elif MC_VER >= 1214
        // 1.21.4+ uses the 12-parameter version with boolean flags
        world.spawnParticles(player, particle, false, false, x, y, z, count, velocityX, velocityY, velocityZ, speed);
        #else
        // 1.21.2 and earlier use the 11-parameter version without boolean flags
        world.spawnParticles(player, particle, false, x, y, z, count, velocityX, velocityY, velocityZ, speed);
        #endif
    }
    
    /**
     * Creates a DustParticleOptions with proper constructor for each version.
     * 
     * @param colorInt RGB color as integer
     * @param size Particle size
     * @return DustParticleOptions instance
     */
    public static DustParticleOptions createDustParticleEffect(int colorInt, float size) {
        #if MC_VER >= 1212
        // 1.21.2+ uses int for color
        return new DustParticleOptions(colorInt, size);
        #else
        // 1.21.1 and earlier use Vector3f for color
        float red = ((colorInt >> 16) & 0xFF) / 255.0f;
        float green = ((colorInt >> 8) & 0xFF) / 255.0f;
        float blue = (colorInt & 0xFF) / 255.0f;
        return new DustParticleOptions(new Vector3f(red, green, blue), size);
        #endif
    }
}



