package com.peek.utils;

import com.peek.data.peek.PlayerPeekData;

import java.util.UUID;

/**
 * Utility class for handling mutual exclusion between blacklist and whitelist
 * When a player is added to one list, they are automatically removed from the other
 */
public class PlayerListMutualExclusion {
    
    /**
     * Adds a player to whitelist and removes them from blacklist if present
     * @param data Current player data
     * @param playerId Player to add to whitelist
     * @return Updated player data with mutual exclusion applied
     */
    public static PlayerPeekData addToWhitelist(PlayerPeekData data, UUID playerId) {
        // First remove from blacklist if present
        PlayerPeekData updated = data.isBlacklisted(playerId) ? 
            data.removeFromBlacklist(playerId) : data;
        
        // Then add to whitelist
        return updated.addToWhitelist(playerId);
    }
    
    /**
     * Adds a player to blacklist and removes them from whitelist if present
     * @param data Current player data
     * @param playerId Player to add to blacklist
     * @return Updated player data with mutual exclusion applied
     */
    public static PlayerPeekData addToBlacklist(PlayerPeekData data, UUID playerId) {
        // First remove from whitelist if present
        PlayerPeekData updated = data.isWhitelisted(playerId) ? 
            data.removeFromWhitelist(playerId) : data;
        
        // Then add to blacklist
        return updated.addToBlacklist(playerId);
    }
    
    /**
     * Checks if adding a player to the specified list would cause a removal from the other list
     * @param data Current player data
     * @param playerId Player to check
     * @param isWhitelist true if adding to whitelist, false if adding to blacklist
     * @return true if the player would be removed from the opposite list
     */
    public static boolean wouldCauseRemoval(PlayerPeekData data, UUID playerId, boolean isWhitelist) {
        if (isWhitelist) {
            return data.isBlacklisted(playerId);
        } else {
            return data.isWhitelisted(playerId);
        }
    }
    
    /**
     * Gets the name of the opposite list for messaging purposes
     * @param isWhitelist true for whitelist, false for blacklist
     * @return The opposite list name
     */
    public static String getOppositeListName(boolean isWhitelist) {
        return isWhitelist ? "blacklist" : "whitelist";
    }
}