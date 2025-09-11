package com.peek.data;

import com.peek.data.peek.PlayerPeekData;
import eu.pb4.playerdata.api.storage.JsonCodecDataStorage;

/**
 * PlayerDataAPI storage instances for Peek mod
 * These handle per-player data and are automatically managed by PlayerDataAPI
 */
public class PeekDataStorage {
    
    /**
     * Storage for individual player peek data (blacklist, private mode, saved state)
     * Using JsonCodecDataStorage for complex nested structures like PlayerState
     * This provides robust serialization for complex data structures with Minecraft objects
     */
    public static final JsonCodecDataStorage<PlayerPeekData> PLAYER_PEEK_DATA_STORAGE = 
        new JsonCodecDataStorage<>("peek_player_data", PlayerPeekData.CODEC);
    
    private PeekDataStorage() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}