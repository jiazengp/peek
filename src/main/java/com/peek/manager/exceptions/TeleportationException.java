package com.peek.manager.exceptions;

import com.peek.manager.constants.ErrorCodes;

/**
 * Exception thrown during teleportation operations
 */
public class TeleportationException extends PeekException {
    
    public enum TeleportOperation {
        SAME_WORLD_TELEPORT,
        CROSS_DIMENSION_TELEPORT,
        POSITION_VALIDATION,
        TELEPORT_BACK
    }
    
    private final TeleportOperation operation;
    
    public TeleportationException(TeleportOperation operation, String errorCode, String message) {
        super(errorCode, message);
        this.operation = operation;
    }
    
    public TeleportationException(TeleportOperation operation, String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.operation = operation;
    }
    
    public TeleportationException(TeleportOperation operation, ErrorCodes errorCode, String message) {
        super(errorCode, message);
        this.operation = operation;
    }
    
    public TeleportationException(TeleportOperation operation, ErrorCodes errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.operation = operation;
    }
    
    public TeleportOperation getOperation() {
        return operation;
    }
    
    @Override
    public String toString() {
        return String.format("%s{operation=%s, errorCode=%s, message=%s}", 
            getClass().getSimpleName(), operation, getErrorCode(), getMessage());
    }
}