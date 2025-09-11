package com.peek.utils;

import com.peek.PeekMod;
import org.slf4j.Logger;

/**
 * Utility class for consistent logging patterns across the peek mod
 */
public final class LoggingHelper {
    
    private static final Logger LOGGER = PeekMod.LOGGER;
    
    private LoggingHelper() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Logs manager shutdown
     */
    public static void logManagerShutdown(String managerName) {
        LOGGER.info("{} shut down", managerName);
    }
    
    /**
     * Logs state saving operations
     */
    public static void logStateSaved(String playerName, String stateType) {
        LOGGER.debug("Saved {} state for player {}", stateType, playerName);
    }
    
    /**
     * Logs state restoration operations
     */
    public static void logStateRestored(String playerName, String stateType) {
        LOGGER.debug("Restored {} state for player {}", stateType, playerName);
    }
    
    /**
     * Logs session operations
     */
    public static void logSessionOperation(String operation, String peekerName, String targetName) {
        LOGGER.info("{} peek session: {} -> {}", operation, peekerName, targetName);
    }
    
    /**
     * Logs session operation with duration
     */
    public static void logSessionWithDuration(String operation, String peekerName, String targetName, long durationSeconds) {
        LOGGER.info("{} peek session: {} -> {} (duration: {}s)", operation, peekerName, targetName, durationSeconds);
    }
    
    /**
     * Logs request operations
     */
    public static void logRequestOperation(String operation, String requesterName, String targetName) {
        LOGGER.debug("{} peek request: {} -> {}", operation, requesterName, targetName);
    }
    
    /**
     * Logs teleportation operations
     */
    public static void logTeleportOperation(String operation, String playerName, Object position) {
        LOGGER.debug("{} teleportation for {}: {}", operation, playerName, position);
    }
}