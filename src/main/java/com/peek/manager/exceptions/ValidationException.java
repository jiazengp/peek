package com.peek.manager.exceptions;

import com.peek.manager.constants.ErrorCodes;

/**
 * Exception thrown during validation operations
 */
public class ValidationException extends PeekException {
    
    public enum ValidationType {
        PLAYER_STATE,
        SESSION_PRECONDITION,
        PERMISSIONS,
        COOLDOWN,
        DISTANCE,
        STATIONARY_CHECK
    }
    
    private final ValidationType validationType;
    
    public ValidationException(ValidationType validationType, String errorCode, String message) {
        super(errorCode, message);
        this.validationType = validationType;
    }
    
    public ValidationException(ValidationType validationType, String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.validationType = validationType;
    }
    
    public ValidationException(ValidationType validationType, ErrorCodes errorCode, String message) {
        super(errorCode, message);
        this.validationType = validationType;
    }
    
    public ValidationException(ValidationType validationType, ErrorCodes errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.validationType = validationType;
    }
    
    public ValidationType getValidationType() {
        return validationType;
    }
    
    @Override
    public String toString() {
        return String.format("%s{validationType=%s, errorCode=%s, message=%s}", 
            getClass().getSimpleName(), validationType, getErrorCode(), getMessage());
    }
}