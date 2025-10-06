package com.peek.utils;

import com.peek.PeekMod;
import com.peek.config.ModConfigManager;
import com.peek.utils.compat.ProfileCompat;
import com.peek.utils.compat.ServerPlayerCompat;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Manages sound notifications for peek operations
 */
public class SoundManager {
    
    /**
     * Plays a sound for request received notification (only to the player)
     */
    public static void playRequestReceivedSound(ServerPlayerEntity player) {
        if (!ModConfigManager.isSoundsEnabled()) return;
        
        playSoundToPlayer(player, 
            ModConfigManager.getRequestReceivedSound(),
            ModConfigManager.getRequestReceivedVolume(),
            ModConfigManager.getRequestReceivedPitch());
    }
    
    /**
     * Plays a sound for invite received notification (only to the player)
     */
    public static void playInviteReceivedSound(ServerPlayerEntity player) {
        if (!ModConfigManager.isSoundsEnabled()) return;
        
        playSoundToPlayer(player,
            ModConfigManager.getInviteReceivedSound(),
            ModConfigManager.getInviteReceivedVolume(),
            ModConfigManager.getInviteReceivedPitch());
    }
    
    /**
     * Plays a sound when teleporting to target (only to the peeker)
     */
    public static void playTeleportToTargetSound(ServerPlayerEntity player) {
        if (!ModConfigManager.isSoundsEnabled()) return;
        
        playSoundToPlayer(player,
            ModConfigManager.getTeleportToTargetSound(),
            ModConfigManager.getTeleportVolume(),
            ModConfigManager.getTeleportPitch());
    }
    
    /**
     * Plays a sound when teleporting back after peek ends (only to the peeker)
     */
    public static void playTeleportBackSound(ServerPlayerEntity player) {
        if (!ModConfigManager.isSoundsEnabled()) return;
        
        playSoundToPlayer(player,
            ModConfigManager.getTeleportBackSound(),
            ModConfigManager.getTeleportBackVolume(),
            ModConfigManager.getTeleportBackPitch());
    }
    
    /**
     * Plays a sound when being peeked by someone (to the world around the target)
     */
    public static void playBeingPeekedSound(ServerPlayerEntity player) {
        if (!ModConfigManager.isSoundsEnabled()) return;
        
        playSoundToWorld(player,
            ModConfigManager.getBeingPeekedSound(),
            ModConfigManager.getBeingPeekedVolume(),
            ModConfigManager.getBeingPeekedPitch());
    }
    
    /**
     * Plays a sound when peek session ends (only to the peeker)
     */
    public static void playSessionEndSound(ServerPlayerEntity player) {
        if (!ModConfigManager.isSoundsEnabled()) return;
        
        playSoundToPlayer(player,
            ModConfigManager.getSessionEndSound(),
            ModConfigManager.getSessionEndVolume(),
            ModConfigManager.getSessionEndPitch());
    }
    
    /**
     * Plays a sound only to a specific player (private sound)
     */
    private static void playSoundToPlayer(ServerPlayerEntity player, String soundId, float volume, float pitch) {
        try {
            Identifier soundIdentifier = Identifier.tryParse(soundId);
            if (soundIdentifier == null) {
                PeekMod.LOGGER.warn("Invalid sound identifier: {}", soundId);
                return;
            }
            
            SoundEvent soundEvent = Registries.SOUND_EVENT.get(soundIdentifier);
            if (soundEvent == null) {
                PeekMod.LOGGER.warn("Sound event not found for identifier: {}", soundId);
                return;
            }
            
            // Clamp values to safe ranges
            volume = Math.max(0.0f, Math.min(1.0f, volume));
            pitch = Math.max(0.1f, Math.min(2.0f, pitch));
            
            // Send sound packet directly to the player (private)
            player.networkHandler.sendPacket(
                new net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket(
                    Registries.SOUND_EVENT.getEntry(soundEvent),
                    SoundCategory.PLAYERS,
                    player.getX(), player.getY(), player.getZ(),
                    volume, pitch, ServerPlayerCompat.getWorld(player).random.nextLong()
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
    private static void playSoundToWorld(ServerPlayerEntity player, String soundId, float volume, float pitch) {
        try {
            Identifier soundIdentifier = Identifier.tryParse(soundId);
            if (soundIdentifier == null) {
                PeekMod.LOGGER.warn("Invalid sound identifier: {}", soundId);
                return;
            }
            
            SoundEvent soundEvent = Registries.SOUND_EVENT.get(soundIdentifier);
            if (soundEvent == null) {
                PeekMod.LOGGER.warn("Sound event not found for identifier: {}", soundId);
                return;
            }
            
            // Clamp values to safe ranges
            volume = Math.max(0.0f, Math.min(1.0f, volume));
            pitch = Math.max(0.1f, Math.min(2.0f, pitch));
            
            // Play sound to the world at player's position (public)
            ServerPlayerCompat.getWorld(player).playSound(null, player.getX(), player.getY(), player.getZ(),
                soundEvent, SoundCategory.PLAYERS, volume, pitch);

            PeekMod.LOGGER.debug("Played world sound {} at {} (volume: {}, pitch: {})",
                soundId, ProfileCompat.getName(player.getGameProfile()), volume, pitch);

        } catch (Exception e) {
            PeekMod.LOGGER.error("Failed to play world sound {} at {}: {}",
                soundId, ProfileCompat.getName(player.getGameProfile()), e.getMessage());
        }
    }
}