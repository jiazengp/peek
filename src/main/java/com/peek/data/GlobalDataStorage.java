package com.peek.data;

import com.peek.data.peek.PeekStatistics;

/**
 * Global data storage instances using custom JsonCodecDataStorage
 * These are not managed by PlayerDataAPI but by our own implementation
 */
public class GlobalDataStorage {
    
    /**
     * Global storage for peek statistics using custom JsonCodecDataStorage
     * This stores global server-wide statistics in the world save directory
     */
    public static final JsonCodecDataStorage<PeekStatistics> PEEK_STATISTICS_STORAGE =
        new JsonCodecDataStorage<>("peek_statistics", PeekStatistics.CODEC);
    
    private GlobalDataStorage() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}