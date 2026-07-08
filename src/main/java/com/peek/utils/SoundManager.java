package com.peek.utils;

import com.peek.PeekMod;
import com.peek.config.ModConfigManager;
import com.peek.utils.compat.ProfileCompat;
import com.peek.utils.compat.ServerPlayerCompat;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;

/**
 * Manages sound notifications for peek operations
 */
public class SoundManager {
    
    /**
     * Plays a sound for request received notification (only to the player)
     */
    public static void playRequestReceivedSound(ServerPlayer player) {
        if (!ModConfigManager.isSoundsEnabled()) return;
        
        playSoundToPlayer(player, 
            ModConfigManager.getRequestReceivedSound(),
            ModConfigManager.getRequestReceivedVolume(),
            ModConfigManager.getRequestReceivedPitch());
    }
    
    /**
     * Plays a sound for invite received notification (only to the player)
     */
    public static void playInviteReceivedSound(ServerPlayer player) {
        if (!ModConfigManager.isSoundsEnabled()) return;
        
        playSoundToPlayer(player,
            ModConfigManager.getInviteReceivedSound(),
            ModConfigManager.getInviteReceivedVolume(),
            ModConfigManager.getInviteReceivedPitch());
    }
    
    /**
     * Plays a sound when teleporting to target (only to the peeker)
     */
    public static void playTeleportToTargetSound(ServerPlayer player) {
        if (!ModConfigManager.isSoundsEnabled()) return;
        
        playSoundToPlayer(player,
            ModConfigManager.getTeleportToTargetSound(),
            ModConfigManager.getTeleportVolume(),
            ModConfigManager.getTeleportPitch());
    }
    
    /**
     * Plays a sound when teleporting back after peek ends (only to the peeker)
     */
    public static void playTeleportBackSound(ServerPlayer player) {
        if (!ModConfigManager.isSoundsEnabled()) return;
        
        playSoundToPlayer(player,
            ModConfigManager.getTeleportBackSound(),
            ModConfigManager.getTeleportBackVolume(),
            ModConfigManager.getTeleportBackPitch());
    }
    
    /**
     * Plays a sound when being peeked by someone (to the world around the target)
     */
    public static void playBeingPeekedSound(ServerPlayer player) {
        if (!ModConfigManager.isSoundsEnabled()) return;
        
        playSoundToWorld(player,
            ModConfigManager.getBeingPeekedSound(),
            ModConfigManager.getBeingPeekedVolume(),
            ModConfigManager.getBeingPeekedPitch());
    }
    
    /**
     * Plays a sound when peek session ends (only to the peeker)
     */
    public static void playSessionEndSound(ServerPlayer player) {
        if (!ModConfigManager.isSoundsEnabled()) return;
        
        playSoundToPlayer(player,
            ModConfigManager.getSessionEndSound(),
            ModConfigManager.getSessionEndVolume(),
            ModConfigManager.getSessionEndPitch());
    }
    
    /**
     * Plays a sound only to a specific player (private sound)
     */
    private static void playSoundToPlayer(ServerPlayer player, String soundId, float volume, float pitch) {
        try {
            Identifier soundIdentifier = Identifier.tryParse(soundId);
            if (soundIdentifier == null) {
                PeekMod.LOGGER.warn("Invalid sound identifier: {}", soundId);
                return;
            }
            
            SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.getValue(soundIdentifier);
            if (soundEvent == null) {
                PeekMod.LOGGER.warn("Sound event not found for identifier: {}", soundId);
                return;
            }
            
            // Clamp values to safe ranges
            volume = Math.max(0.0f, Math.min(1.0f, volume));
            pitch = Math.max(0.1f, Math.min(2.0f, pitch));
            
            // Send sound packet directly to the player (private)
            player.connection.send(
                new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundEvent),
                    SoundSource.PLAYERS,
                    player.getX(), player.getY(), player.getZ(),
                    volume, pitch, ServerPlayerCompat.getWorld(player).getRandom().nextLong()
                )
            );

            PeekMod.LOGGER.debug("Played private sound {} to {} (volume: {}, pitch: {})",
                soundId, ProfileCompat.getName(player.getGameProfile()), volume, pitch);

        } catch (Exception e) {
            PeekMod.LOGGER.error("Failed to play private sound {} to {}: {}",
                soundId, ProfileCompat.getName(player.getGameProfile()), e.getMessage());
        }
    }
    
    /**
     * Plays a sound to the world around a player (public sound)
     */
    private static void playSoundToWorld(ServerPlayer player, String soundId, float volume, float pitch) {
        try {
            Identifier soundIdentifier = Identifier.tryParse(soundId);
            if (soundIdentifier == null) {
                PeekMod.LOGGER.warn("Invalid sound identifier: {}", soundId);
                return;
            }
            
            SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.getValue(soundIdentifier);
            if (soundEvent == null) {
                PeekMod.LOGGER.warn("Sound event not found for identifier: {}", soundId);
                return;
            }
            
            // Clamp values to safe ranges
            volume = Math.max(0.0f, Math.min(1.0f, volume));
            pitch = Math.max(0.1f, Math.min(2.0f, pitch));
            
            // Play sound to the world at player's position (public)
            ServerPlayerCompat.getWorld(player).playSound(null, player.getX(), player.getY(), player.getZ(),
                soundEvent, SoundSource.PLAYERS, volume, pitch);

            PeekMod.LOGGER.debug("Played world sound {} at {} (volume: {}, pitch: {})",
                soundId, ProfileCompat.getName(player.getGameProfile()), volume, pitch);

        } catch (Exception e) {
            PeekMod.LOGGER.error("Failed to play world sound {} at {}: {}",
                soundId, ProfileCompat.getName(player.getGameProfile()), e.getMessage());
        }
    }
}




