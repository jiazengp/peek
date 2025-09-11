package com.peek.manager.constants;

/**
 * Constants related to peek sessions
 */
public final class SessionConstants {
    
    // Thread names
    public static final String STATS_CLEANUP_THREAD_NAME = "peek-stats-cache-cleanup";
    
    // Cache keys
    public static final String PLAYER_STATS_CACHE_KEY_PREFIX = "player_stats_";
    public static final String GLOBAL_STATS_CACHE_KEY = "global_stats";
    
    private SessionConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}