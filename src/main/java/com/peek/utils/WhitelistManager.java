package com.peek.utils;

import com.peek.data.peek.PlayerPeekData;

import java.util.Map;
import java.util.UUID;

/**
 * Concrete implementation for managing whitelist operations
 */
public class WhitelistManager extends AbstractPlayerListManager {
    
    @Override
    protected String getListType() {
        return "whitelist";
    }
    
    @Override
    protected String getListTypeCapitalized() {
        return "Whitelist";
    }
    
    @Override
    protected Map<UUID, Long> getList(PlayerPeekData data) {
        return data.whitelist();
    }
    
    @Override
    protected PlayerPeekData updateList(PlayerPeekData data, Map<UUID, Long> newList) {
        // This method is not currently used but could be useful for bulk operations
        throw new UnsupportedOperationException("Use specific add/remove methods instead");
    }
    
    @Override
    protected PlayerPeekData addWithMutualExclusion(PlayerPeekData data, UUID playerId) {
        // This will automatically remove from blacklist if present
        return PlayerListMutualExclusion.addToWhitelist(data, playerId);
    }
    
    @Override
    protected PlayerPeekData removeFromList(PlayerPeekData data, UUID playerId) {
        return data.removeFromWhitelist(playerId);
    }
}