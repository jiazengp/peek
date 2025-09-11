package com.peek.manager.constants;

/**
 * Core constants for Peek mod operations
 */
public final class PeekConstants {
    
    // Default values (these should be overridden by config)
    public static final int DEFAULT_STATIC_TICKS = 20; // 1 second

    // Cleanup intervals
    public static final long CLEANUP_INTERVAL_SECONDS = 60;
    public static final long SESSION_UPDATE_INTERVAL_TICKS = 20; // 1 second

    // Sorting options for admin commands
    public enum SortType {
        PEEK_COUNT("peek_count"),
        PEEKED_COUNT("peeked_count"), 
        TOTAL_DURATION("total_duration"),
        LAST_ACTIVITY("last_activity"),
        PLAYER_NAME("player_name");
        
        private final String key;
        
        SortType(String key) {
            this.key = key;
        }
        
        public String getKey() {
            return key;
        }
        
        public static SortType fromKey(String key) {
            for (SortType type : values()) {
                if (type.key.equals(key)) {
                    return type;
                }
            }
            return PEEK_COUNT; // default
        }
    }
    
    // Result wrapper for operations
    public static class Result<T> {
        private final T value;
        private final boolean success;
        private final String error;
        
        private Result(T value, boolean success, String error) {
            this.value = value;
            this.success = success;
            this.error = error;
        }
        
        public static <T> Result<T> success(T value) {
            return new Result<>(value, true, null);
        }
        
        public static <T> Result<T> failure(String error) {
            return new Result<>(null, false, error);
        }
        
        public static <T> Result<T> failure(ErrorCodes errorCode) {
            return new Result<>(null, false, errorCode.getTranslationKey());
        }
        
        public boolean isSuccess() { return success; }
        public T getValue() { return value; }
        public String getError() { return error; }
    }
    
    private PeekConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}