package com.peek.manager.constants;

/**
 * Centralized error codes for peek operations
 */
public enum ErrorCodes {
    
    // Validation errors
    VALIDATION_FAILED("peek.error.validation_failed"),
    HOSTILE_MOBS_NEARBY("peek.error.hostile_mobs_nearby"),
    TOO_CLOSE_SAME_DIMENSION("peek.error.too_close_same_dimension"),
    DISTANCE_EXCEEDED("peek.error.distance_exceeded"),
    DIMENSION_NOT_ALLOWED("peek.error.dimension_not_allowed"),
    PLAYER_NOT_STATIONARY("peek.error.player_not_moving"),
    COOLDOWN_ACTIVE("peek.error.on_cooldown"),
    
    // Session errors
    SESSION_NOT_FOUND("peek.error.session_not_found"),
    SESSION_ALREADY_EXISTS("peek.error.session_already_exists"),
    SESSION_LIMIT_EXCEEDED("peek.error.max_sessions"),
    SESSION_CLEANUP_FAILED("peek.error.session_cleanup_failed"),
    ALREADY_PEEKING("peek.error.already_peeking"),
    ALREADY_PEEKING_TARGET("peek.error.already_peeking_target"),
    BEING_PEEKED("peek.error.being_peeked"),
    NOT_PEEKING_ANYONE("peek.error.not_peeking_anyone"),
    
    // Request errors
    REQUEST_NOT_FOUND("peek.error.no_pending_request"),
    REQUEST_EXPIRED("peek.error.request_expired"),
    REQUEST_ALREADY_EXISTS("peek.error.request_already_exists"),
    REQUEST_LIMIT_EXCEEDED("peek.error.request_limit_exceeded"),
    TARGET_PROCESSING_REQUEST("peek.error.target_processing_request"),
    
    // Permission errors
    INSUFFICIENT_PERMISSIONS("peek.error.no_permission"),
    PRIVATE_MODE("peek.error.private_mode"),
    BLACKLISTED("peek.error.blacklisted"),
    
    // Teleport errors
    TELEPORT_FAILED("peek.error.teleport_failed"),
    CROSS_DIMENSION_FAILED("peek.error.cross_dimension_failed"),
    UNSAFE_LOCATION("peek.error.unsafe_location"),
    
    // Player errors
    PLAYER_NOT_FOUND("peek.error.player_not_found"),
    PLAYER_OFFLINE("peek.error.player_offline"),
    CANNOT_PEEK_SELF("peek.error.cannot_peek_self"),
    
    // Invite-related errors
    DUPLICATE_INVITE("peek.error.duplicate_invite"),
    INVITE_NOT_FOUND("peek.error.invite_not_found"),
    INVITE_EXPIRED("peek.error.invite_expired"),
    
    // Concurrency errors
    LOCK_TIMEOUT("peek.error.lock_timeout"),
    RACE_CONDITION("peek.error.race_condition"),
    DEADLOCK_DETECTED("peek.error.deadlock_detected"),
    RESOURCE_BUSY("peek.error.resource_busy"),
    CONCURRENT_MODIFICATION("peek.error.concurrent_modification"),
    ATOMIC_CLEANUP_FAILED("peek.error.atomic_cleanup_failed"),
    
    // Generic errors
    INTERNAL_ERROR("peek.error.internal"),
    OPERATION_FAILED("peek.error.operation_failed");
    
    private final String translationKey;
    
    ErrorCodes(String translationKey) {
        this.translationKey = translationKey;
    }
    
    public String getTranslationKey() {
        return translationKey;
    }
}