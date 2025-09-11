package com.peek.data.peek;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Stores player-specific peek settings and data
 * This will be stored using PlayerDataAPI for each individual player
 */
public record PlayerPeekData(
    boolean privateMode,
    boolean autoAccept,
    Map<UUID, Long> blacklist,  // UUID -> timestamp when added to blacklist
    Map<UUID, Long> whitelist,  // UUID -> timestamp when added to whitelist
    PlayerState savedState  // State saved during peek session for crash recovery (null if none)
) {
    
    /**
     * Compact constructor for validation and null safety
     */
    public PlayerPeekData {
        // Handle null maps for backward compatibility (from old save data)
        blacklist = blacklist != null ? blacklist : new HashMap<>();
        whitelist = whitelist != null ? whitelist : new HashMap<>();
        
        // Handle potential issues with saved state from old versions
        // The PlayerState record structure change might cause deserialization issues
        // but Gson should handle this gracefully by setting missing fields to defaults
    }
    
    // Codec for robust serialization including the complex PlayerState
    public static final Codec<PlayerPeekData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.fieldOf("privateMode").forGetter(PlayerPeekData::privateMode),
        Codec.BOOL.fieldOf("autoAccept").forGetter(PlayerPeekData::autoAccept),
        Codec.unboundedMap(net.minecraft.util.Uuids.CODEC, Codec.LONG).fieldOf("blacklist").forGetter(PlayerPeekData::blacklist),
        Codec.unboundedMap(net.minecraft.util.Uuids.CODEC, Codec.LONG).fieldOf("whitelist").forGetter(PlayerPeekData::whitelist),
        PlayerState.CODEC.optionalFieldOf("savedState").forGetter((PlayerPeekData data) -> Optional.ofNullable(data.savedState()))
    ).apply(instance, (privateMode, autoAccept, blacklist, whitelist, savedState) -> 
        new PlayerPeekData(privateMode, autoAccept, blacklist, whitelist, savedState.orElse(null))));
    
    /**
     * Creates default peek data for a new player
     */
    public static PlayerPeekData createDefault() {
        return new PlayerPeekData(false, false, new HashMap<>(), new HashMap<>(), null);
    }
    
    /**
     * Safely gets player data with backward compatibility handling
     * @param player Player to get data for
     * @return Player data with guaranteed non-null maps
     */
    public static PlayerPeekData getOrCreate(ServerPlayerEntity player) {
        try {
            PlayerPeekData data = eu.pb4.playerdata.api.PlayerDataApi.getCustomDataFor(player, 
                com.peek.data.PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
            if (data == null) {
                com.peek.PeekMod.LOGGER.debug("No existing player data found for {} - creating default", 
                    player.getGameProfile().getName());
                return createDefault();
            }
            
            com.peek.PeekMod.LOGGER.debug("Loaded existing player data for {} - hasState: {}", 
                player.getGameProfile().getName(), data.hasSavedState());
            return data;
        } catch (Exception e) {
            com.peek.PeekMod.LOGGER.error("Error loading player data for {} - creating default. Error: {}", 
                player.getGameProfile().getName(), e.getMessage(), e);
            return createDefault();
        }
    }
    
    /**
     * Checks if a player is blacklisted
     */
    public boolean isBlacklisted(UUID playerId) {
        return blacklist.containsKey(playerId);
    }
    
    /**
     * Adds a player to blacklist with current timestamp
     */
    public PlayerPeekData addToBlacklist(UUID playerId) {
        Map<UUID, Long> newBlacklist = new HashMap<>(blacklist);
        newBlacklist.put(playerId, Instant.now().toEpochMilli());
        return new PlayerPeekData(privateMode, autoAccept, newBlacklist, whitelist, savedState);
    }
    
    /**
     * Removes a player from blacklist
     */
    public PlayerPeekData removeFromBlacklist(UUID playerId) {
        Map<UUID, Long> newBlacklist = new HashMap<>(blacklist);
        newBlacklist.remove(playerId);
        return new PlayerPeekData(privateMode, autoAccept, newBlacklist, whitelist, savedState);
    }
    
    /**
     * Updates private mode setting
     */
    public PlayerPeekData withPrivateMode(boolean privateMode) {
        // If enabling private mode, disable auto accept (they are mutually exclusive)
        boolean newAutoAccept = !privateMode && this.autoAccept;
        return new PlayerPeekData(privateMode, newAutoAccept, blacklist, whitelist, savedState);
    }
    
    /**
     * Updates saved state
     */
    public PlayerPeekData withSavedState(PlayerState savedState) {
        return new PlayerPeekData(privateMode, autoAccept, blacklist, whitelist, savedState);
    }
    
    /**
     * Checks if there is a saved state
     */
    public boolean hasSavedState() {
        return savedState != null;
    }
    
    /**
     * Updates auto accept setting
     */
    public PlayerPeekData withAutoAccept(boolean autoAccept) {
        // If enabling auto accept, disable private mode (they are mutually exclusive)
        boolean newPrivateMode = !autoAccept && this.privateMode;
        return new PlayerPeekData(newPrivateMode, autoAccept, blacklist, whitelist, savedState);
    }
    
    /**
     * Checks if peek requests are allowed based on private mode and blacklist
     */
    public boolean canReceivePeekFrom(UUID requesterId) {
        if (privateMode) return false;
        return !isBlacklisted(requesterId);
    }
    
    /**
     * Gets the timestamp when a player was blacklisted
     */
    public Long getBlacklistTimestamp(UUID playerId) {
        return blacklist.get(playerId);
    }
    
    /**
     * Checks if a player is whitelisted
     */
    public boolean isWhitelisted(UUID playerId) {
        return whitelist.containsKey(playerId);
    }
    
    /**
     * Adds a player to whitelist with current timestamp
     */
    public PlayerPeekData addToWhitelist(UUID playerId) {
        Map<UUID, Long> newWhitelist = new HashMap<>(whitelist);
        newWhitelist.put(playerId, Instant.now().toEpochMilli());
        return new PlayerPeekData(privateMode, autoAccept, blacklist, newWhitelist, savedState);
    }
    
    /**
     * Removes a player from whitelist
     */
    public PlayerPeekData removeFromWhitelist(UUID playerId) {
        Map<UUID, Long> newWhitelist = new HashMap<>(whitelist);
        newWhitelist.remove(playerId);
        return new PlayerPeekData(privateMode, autoAccept, blacklist, newWhitelist, savedState);
    }
    
    /**
     * Gets the timestamp when a player was whitelisted
     */
    public Long getWhitelistTimestamp(UUID playerId) {
        return whitelist.get(playerId);
    }
}