package com.peek.data.peek;

// Using Mojang Codec for robust serialization of complex structures
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.peek.utils.compat.ProfileCompat;
import com.peek.utils.compat.ServerPlayerCompat;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Stores a player's state that needs to be restored after peek session ends
 * Supports serialization for persistence across server restarts
 */
public record PlayerState(
    Vec3 position,
    float yaw,
    float pitch,
    UUID worldId, 
    GameType gameMode,
    List<MobEffectInstance> statusEffects, // Use proper MobEffectInstance objects
    int fireTicks, // Fire/lava state
    int air, // Oxygen level (for underwater)
    int vehicleBubbleTime // Vehicle bubble state (for entities like striders)
) {
    
    // Codec for robust serialization of complex structures
    public static final Codec<PlayerState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Vec3.CODEC.fieldOf("position").forGetter(PlayerState::position),
        Codec.FLOAT.fieldOf("yaw").forGetter(PlayerState::yaw),
        Codec.FLOAT.fieldOf("pitch").forGetter(PlayerState::pitch),
        net.minecraft.core.UUIDUtil.CODEC.fieldOf("worldId").forGetter(PlayerState::worldId),
        GameType.CODEC.fieldOf("gameMode").forGetter(PlayerState::gameMode),
        MobEffectInstance.CODEC.listOf().fieldOf("statusEffects").forGetter(PlayerState::statusEffects),
        Codec.INT.fieldOf("fireTicks").forGetter(PlayerState::fireTicks),
        Codec.INT.fieldOf("air").forGetter(PlayerState::air),
        Codec.INT.fieldOf("vehicleBubbleTime").forGetter(PlayerState::vehicleBubbleTime)
    ).apply(instance, PlayerState::new));
    
    /**
     * Captures the current state of a player
     */
    public static PlayerState capture(ServerPlayer player, HolderLookup.Provider registryLookup) {
        // Capture position and world
        Vec3 position = ServerPlayerCompat.getPos(player);
        UUID worldId = UUID.nameUUIDFromBytes(
            ServerPlayerCompat.getWorld(player).dimension().identifier().toString().getBytes()
        );
        
        // Capture game mode
        GameType gameMode = com.peek.utils.compat.PlayerCompat.getGameMode(player);
        
        // Capture rotation
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        
        // Capture status effects directly as MobEffectInstance objects
        Collection<MobEffectInstance> activeEffects = player.getActiveEffects();
        List<MobEffectInstance> statusEffects = activeEffects.stream()
            .filter(effect -> effect != null && effect.getDuration() > 0) // Only save effects with remaining duration
            .toList();
        
        // Capture special states that need manual restoration
        int fireTicks = player.getRemainingFireTicks(); // Fire/lava state
        int air = player.getAirSupply(); // Oxygen level
        
        // Vehicle bubble time - this is more complex and may need different handling
        // For now, we'll capture it but it might not be fully functional without more research
        int vehicleBubbleTime = 0;
        try {
            // This field might not be directly accessible, we'll set it to 0 for now
            // In a full implementation, this would require reflection or mixin to access
            vehicleBubbleTime = 0;
        } catch (Exception e) {
            // Ignore if we can't access vehicle bubble time
            vehicleBubbleTime = 0;
        }
        
        
        return new PlayerState(
            position, yaw, pitch, worldId, gameMode, statusEffects, fireTicks, air, vehicleBubbleTime
        );
    }
    
    /**
     * Restores this state to a player
     */
    public void restore(ServerPlayer player, HolderLookup.Provider registryLookup) {
        try {
            // Clear any effects gained during peek (beacon effects, potions, etc.) before restoring
            player.removeAllEffects();
            
            // Restore game mode
            player.setGameMode(gameMode);
            
            // Restore position - teleport player back to original location (may throw exception)
            restorePosition(player);
            
            // Clear effects again after teleport (player might have gained beacon/area effects at original location)
            player.removeAllEffects();
            
            // Restore special states that need manual restoration
            // Health and food are automatically handled by the game, so we skip them
            
            // Restore fire state
            player.setRemainingFireTicks(fireTicks);
            
            // Restore air (oxygen) level
            player.setAirSupply(air);

            // Note: Experience, health, and food are automatically saved/restored by the game
            // Restore the original effects directly from MobEffectInstance objects
            if (statusEffects != null) {
                for (MobEffectInstance effect : statusEffects) {
                    if (effect != null && effect.getDuration() > 0) {
                        boolean success = player.addEffect(effect);
                    }
                }
            }
            
            
        } catch (Exception e) {
            // If any part of restoration fails, log it and re-throw
            com.peek.PeekMod.LOGGER.error("Failed to restore state for player {}", ProfileCompat.getName(player.getGameProfile()), e);
            throw e; // Re-throw to let caller handle the failure
        }
    }
    
    /**
     * Restores the player's position, handling cross-world teleportation
     */
    private void restorePosition(ServerPlayer player) {
        try {
            // Find the target world
            ServerLevel targetWorld = null;

            // Try to find the world by comparing world registry key hash
            if (ServerPlayerCompat.getServer(player) == null) {
                return;
            }

            for (ServerLevel world : ServerPlayerCompat.getServer(player).getAllLevels()) {
                UUID currentWorldId = UUID.nameUUIDFromBytes(
                    world.dimension().identifier().toString().getBytes()
                );
                if (currentWorldId.equals(worldId)) {
                    targetWorld = world;
                    break;
                }
            }
            
            // If world not found, use the current world as fallback
            if (targetWorld == null) {
                targetWorld = com.peek.utils.compat.PlayerCompat.getServerWorld(player);
            }
            
            // Perform the teleportation with original rotation
            com.peek.utils.compat.PlayerCompat.teleport(player, targetWorld, position.x, position.y, position.z, yaw, pitch);
                
        } catch (Exception e) {
            // If teleportation fails, log the error but don't crash
            // The player will remain at their current position
            com.peek.PeekMod.LOGGER.error("Failed to restore position for player {} to world {} at {}",
                ProfileCompat.getName(player.getGameProfile()), worldId, position, e);
            throw new RuntimeException("Position restoration failed", e);
        }
    }
}




