package com.peek.utils;

import com.peek.PeekMod;
import com.peek.config.ModConfigManager;
import com.peek.manager.constants.GameConstants;
import com.peek.utils.compat.ProfileCompat;
import com.peek.utils.compat.ServerPlayerCompat;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Simple particle effects utility for peek sessions
 * Only tracks players that need particle effects with their tick counters
 */
public class ParticleEffectManager {
    
    private static final Random RANDOM = new Random();
    
    // 颜色验证的正则表达式模式
    private static final String HEX_COLOR_PATTERN = "^[0-9A-Fa-f]{6}$";
    private static final Pattern HEX_PATTERN = Pattern.compile(HEX_COLOR_PATTERN);
    
    // Simple tracking: just the players that need particle effects and their tick counters
    private static final ConcurrentHashMap<UUID, Integer> playerTickCounters = new ConcurrentHashMap<>();
    
    // Track peeker -> target mapping for visibility control (1:1)
    private static final ConcurrentHashMap<UUID, UUID> peekerToTarget = new ConcurrentHashMap<>();
    
    // Reverse index: target -> set of peekers for efficient cleanup (1:many)
    private static final ConcurrentHashMap<UUID, Set<UUID>> targetToPeekers = new ConcurrentHashMap<>();
    
    /**
     * Process particle effects for currently tracked peekers
     * Called from PeekSessionManager.onServerTick()
     */
    public static void processParticleEffects(ServerWorld world) {
        if (!ModConfigManager.isParticleEffectsEnabled()) {
            return;
        }
        
        int frequencyTicks = Math.max(1, ModConfigManager.getParticleFrequencyTicks());
        
        // Process particles for each tracked peeker
        for (UUID peekerId : playerTickCounters.keySet()) {
            // Update tick counter for this player
            int tickCount = playerTickCounters.getOrDefault(peekerId, 0) + 1;
            playerTickCounters.put(peekerId, tickCount);
            
            // Only spawn particles at configured frequency
            if (tickCount >= frequencyTicks) {
                playerTickCounters.put(peekerId, 0); // Reset counter
                
                ServerPlayerEntity peeker = world.getServer().getPlayerManager().getPlayer(peekerId);
                if (peeker != null && peeker.isSpectator()) {
                    spawnParticleEffectForPlayer(peeker, world);
                }
            }
        }
    }
    
    /**
     * Add a player to particle effect tracking
     * Called when a player starts peeking
     */
    public static void addPlayer(UUID peekerId, UUID targetId) {
        if (ModConfigManager.isParticleEffectsEnabled()) {
            playerTickCounters.put(peekerId, 0); // Start with 0 ticks
            peekerToTarget.put(peekerId, targetId); // Track target for visibility
            
            // Maintain reverse index: target -> set of peekers
            targetToPeekers.computeIfAbsent(targetId, k -> ConcurrentHashMap.newKeySet()).add(peekerId);
            
            PeekMod.LOGGER.debug("Added peeker {} targeting {} to particle effects", peekerId, targetId);
        }
    }
    
    /**
     * Remove a player from particle effect tracking
     * Called when a player stops peeking
     */
    public static void removePlayer(UUID peekerId, UUID targetId) {
        UUID recordedTargetId = peekerToTarget.get(peekerId);
        
        // Validate that the target matches what we have recorded
        if (recordedTargetId != null && !recordedTargetId.equals(targetId)) {
            PeekMod.LOGGER.warn("Target ID mismatch when removing peeker {}: expected {}, got {}", 
                peekerId, recordedTargetId, targetId);
            // Use recorded target for cleanup to prevent data inconsistency
            targetId = recordedTargetId;
        }
        
        // Remove from main mappings
        playerTickCounters.remove(peekerId);
        peekerToTarget.remove(peekerId);
        
        // Maintain reverse index: remove peeker from target's peeker set
        Set<UUID> peekers = targetToPeekers.get(targetId);
        if (peekers != null) {
            peekers.remove(peekerId);
            // If no more peekers for this target, remove the entry entirely
            if (peekers.isEmpty()) {
                targetToPeekers.remove(targetId);
            }
        }
        
        PeekMod.LOGGER.debug("Removed peeker {} (target: {}) from particle effects", peekerId, targetId);
    }
    
    /**
     * Clean up particle tracking for a specific player
     * Called when player disconnects - SessionManager will handle stopping sessions
     */
    public static void cleanupPlayerParticles(UUID playerId) {
        // Simple cleanup: just remove this player's particle data
        // SessionManager already handles stopping all related sessions properly
        UUID targetId = peekerToTarget.get(playerId);
        if (targetId != null) {
            removePlayer(playerId, targetId);
        } else {
            // Direct cleanup if no mapping exists
            playerTickCounters.remove(playerId);
            peekerToTarget.remove(playerId);
        }
        PeekMod.LOGGER.debug("Cleaned up particle data for player {}", playerId);
    }
    
    /**
     * Clear all particle tracking (shutdown)
     */
    public static void shutdown() {
        playerTickCounters.clear();
        peekerToTarget.clear();
        targetToPeekers.clear();
        PeekMod.LOGGER.debug("ParticleEffectManager shutdown completed");
    }
    
    /**
     * Spawn particle effect for a specific player
     */
    private static void spawnParticleEffectForPlayer(ServerPlayerEntity peeker, ServerWorld world) {
        try {
            UUID peekerId = peeker.getUuid();
            UUID targetId = peekerToTarget.get(peekerId);
            
            if (targetId == null) {
                return; // No target mapping found
            }
            
            // Get peeker's current position
            Vec3d peekerPos = ServerPlayerCompat.getPos(peeker);
            
            // Create particle effect
            ParticleEffect particleEffect = createParticleEffect();
            if (particleEffect == null) {
                return; // Invalid particle type configured
            }
            
            // Configuration values with constants
            int particleCount = Math.max(GameConstants.MIN_PARTICLES_PER_SPAWN, 
                Math.min(GameConstants.MAX_PARTICLES_PER_SPAWN, ModConfigManager.getParticlesPerSpawn()));
            double spread = Math.max(GameConstants.MIN_PARTICLE_SPREAD, 
                Math.min(GameConstants.MAX_PARTICLE_SPREAD, ModConfigManager.getParticleSpread()));
            double velocity = Math.max(GameConstants.MIN_PARTICLE_VELOCITY, 
                Math.min(GameConstants.MAX_PARTICLE_VELOCITY, ModConfigManager.getParticleVelocity()));
            
            // Spawn particles around the peeker with some randomization to avoid blocking view
            for (int i = 0; i < particleCount; i++) {
                // Create particles around peeker, but avoid direct line of sight
                // Spawn particles slightly behind and to the sides to minimize view obstruction
                double offsetX = (RANDOM.nextDouble() - 0.5) * spread * 2;
                double offsetY = (RANDOM.nextDouble() - 0.3) * spread; // Bias towards lower positions
                double offsetZ = (RANDOM.nextDouble() - 0.5) * spread * 2;
                
                // Avoid particles directly in front of the player's view
                if (Math.abs(offsetX) < GameConstants.PARTICLE_VIEW_OBSTRUCTION_X_THRESHOLD && 
                    offsetZ > GameConstants.PARTICLE_VIEW_OBSTRUCTION_Z_THRESHOLD) {
                    offsetZ -= GameConstants.PARTICLE_PUSH_BACK_DISTANCE; // Push particles behind the player
                }
                
                Vec3d particlePos = peekerPos.add(offsetX, offsetY, offsetZ);
                
                // Randomize particle velocity to create natural movement
                double velocityX = (RANDOM.nextDouble() - 0.5) * velocity;
                double velocityY = RANDOM.nextDouble() * velocity * GameConstants.PARTICLE_UPWARD_VELOCITY_BIAS; // Slight upward bias
                double velocityZ = (RANDOM.nextDouble() - 0.5) * velocity;
                
                // Determine who should see the particles
                if (ModConfigManager.isParticleOnlyVisibleToTarget()) {
                    // Only spawn for the target player
                    ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(targetId);
                    if (target != null) {
                        spawnParticleForPlayer(target, particleEffect, particlePos, velocityX, velocityY, velocityZ);
                    }
                } else {
                    // Spawn for nearby players within view distance
                    spawnParticleForNearbyPlayers(world, particleEffect, particlePos, velocityX, velocityY, velocityZ, peekerPos);
                }
            }
            
            PeekMod.LOGGER.debug("Spawned {} particles around peeker {}", particleCount, ProfileCompat.getName(peeker.getGameProfile()));

        } catch (Exception e) {
            PeekMod.LOGGER.error("Failed to spawn particle effects for player {}: {}", ProfileCompat.getName(peeker.getGameProfile()), e.getMessage());
        }
    }
    
    /**
     * Create the configured particle effect
     */
    private static ParticleEffect createParticleEffect() {
        String particleType = ModConfigManager.getParticleType().toUpperCase();
        
        try {
            switch (particleType) {
                case "FLAME":
                    return ParticleTypes.FLAME;
                case "PORTAL":
                    return ParticleTypes.PORTAL;
                case "ENCHANT":
                    return ParticleTypes.ENCHANT;
                case "HEART":
                    return ParticleTypes.HEART;
                case "NOTE":
                    return ParticleTypes.NOTE;
                case "SPARKLE":
                    return ParticleTypes.END_ROD;
                case "DUST":
                    // Parse color from hex string
                    String colorHex = ModConfigManager.getDustParticleColor();
                    int colorInt = parseHexColorToInt(colorHex);
                    return com.peek.utils.compat.ParticleCompat.createDustParticleEffect(colorInt, 1.0f);
                default:
                    PeekMod.LOGGER.warn("Unknown particle type '{}', falling back to ENCHANT", particleType);
                    return ParticleTypes.ENCHANT;
            }
        } catch (Exception e) {
            PeekMod.LOGGER.error("Failed to create particle effect for type '{}': {}", particleType, e.getMessage());
            return ParticleTypes.ENCHANT; // Fallback
        }
    }
    
    /**
     * Parse hex color string to int for DustParticleEffect
     */
    private static int parseHexColorToInt(String hexColor) {
        if (hexColor == null || hexColor.trim().isEmpty()) {
            PeekMod.LOGGER.warn("Null or empty hex color provided, using default cyan");
            return GameConstants.DEFAULT_PARTICLE_COLOR;
        }

        hexColor = hexColor.trim().replace("#", "").toUpperCase();

        if (!HEX_PATTERN.matcher(hexColor).matches()) {
            PeekMod.LOGGER.warn("Invalid hex color format '{}', must be exactly {} hex digits (0-9, A-F), using default cyan", 
                hexColor, GameConstants.HEX_COLOR_LENGTH);
            return GameConstants.DEFAULT_PARTICLE_COLOR;
        }

        try {
            int color = Integer.parseInt(hexColor, 16);
            PeekMod.LOGGER.debug("Successfully parsed hex color '{}' to integer: 0x{}", hexColor, Integer.toHexString(color));
            return color;
        } catch (NumberFormatException e) {
            PeekMod.LOGGER.warn("Failed to parse hex color '{}' to integer: {}, using default cyan", hexColor, e.getMessage());
            return GameConstants.DEFAULT_PARTICLE_COLOR;
        }
    }
    
    /**
     * Spawn particle for a specific player only
     */
    private static void spawnParticleForPlayer(ServerPlayerEntity player, ParticleEffect particle, 
                                             Vec3d pos, double velocityX, double velocityY, double velocityZ) {
        try {
            ServerWorld world = com.peek.utils.compat.PlayerCompat.getServerWorld(player);
            // Use the compatible spawnParticles method
            com.peek.utils.compat.ParticleCompat.spawnParticles(world, player, particle, pos.x, pos.y, pos.z, 1, velocityX, velocityY, velocityZ, 0);
        } catch (Exception e) {
            PeekMod.LOGGER.debug("Failed to spawn particle for player {}: {}", ProfileCompat.getName(player.getGameProfile()), e.getMessage());
        }
    }
    
    /**
     * Spawn particles for nearby players within view distance
     */
    private static void spawnParticleForNearbyPlayers(ServerWorld world, ParticleEffect particle, 
                                                    Vec3d pos, double velocityX, double velocityY, double velocityZ, Vec3d centerPos) {
        try {
            double maxViewDistance = ModConfigManager.getParticleMaxViewDistance();
            
            if (maxViewDistance <= 0) {
                // No distance limit, spawn for all players in the dimension
                world.spawnParticles(particle, pos.x, pos.y, pos.z, 1, velocityX, velocityY, velocityZ, 0);
            } else {
                // Spawn only for players within view distance
                for (ServerPlayerEntity player : world.getPlayers()) {
                    double distance = ServerPlayerCompat.getPos(player).distanceTo(centerPos);
                    if (distance <= maxViewDistance) {
                        spawnParticleForPlayer(player, particle, pos, velocityX, velocityY, velocityZ);
                    }
                }
            }
        } catch (Exception e) {
            PeekMod.LOGGER.debug("Failed to spawn particles for nearby players: {}", e.getMessage());
        }
    }
    
    // === Simple query methods ===
    
    /**
     * Check if a player is currently being tracked for particle effects
     */
    public static boolean isPlayerTracked(UUID playerId) {
        return playerTickCounters.containsKey(playerId);
    }
    
    /**
     * Get total number of players being tracked for particle effects
     */
    public static int getTotalTrackedPlayers() {
        return playerTickCounters.size();
    }
    
    /**
     * Get debug information about current particle tracking state
     */
    public static String getDebugInfo() {
        return String.format("ParticleEffectManager: %d tracked peekers, %d targets", 
            playerTickCounters.size(), targetToPeekers.size());
    }
    
    /**
     * Get all peekers targeting a specific target (for debugging)
     */
    public static Set<UUID> getPeekersTargeting(UUID targetId) {
        Set<UUID> peekers = targetToPeekers.get(targetId);
        return peekers != null ? new HashSet<>(peekers) : new HashSet<>();
    }
}