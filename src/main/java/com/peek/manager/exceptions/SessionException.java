package com.peek.manager.exceptions;

import com.peek.manager.constants.ErrorCodes;
import java.util.UUID;

/**
 * Exception thrown during session operations
 */
public class SessionException extends PeekException {
    private final UUID sessionId;
    private final UUID peekerId;
    private final UUID targetId;
    private final SessionOperation operation;
    
    public enum SessionOperation {
        START,
        STOP,
        UPDATE,
        TELEPORT,
        VALIDATE,
        CLEANUP
    }
    
    public SessionException(SessionOperation operation, String errorCode, String message) {
        this(operation, errorCode, message, null, null, null);
    }
    
    public SessionException(SessionOperation operation, String errorCode, String message, 
                           UUID sessionId, UUID peekerId, UUID targetId) {
        super(errorCode, message);
        this.operation = operation;
        this.sessionId = sessionId;
        this.peekerId = peekerId;
        this.targetId = targetId;
    }
    
    public SessionException(SessionOperation operation, String errorCode, String message, Throwable cause) {
        this(operation, errorCode, message, cause, null, null, null);
    }
    
    public SessionException(SessionOperation operation, String errorCode, String message, Throwable cause,
                           UUID sessionId, UUID peekerId, UUID targetId) {
        super(errorCode, message, cause);
        this.operation = operation;
        this.sessionId = sessionId;
        this.peekerId = peekerId;
        this.targetId = targetId;
    }
    
    public SessionException(SessionOperation operation, ErrorCodes errorCode, String message) {
        this(operation, errorCode, message, null, null, null);
    }
    
    public SessionException(SessionOperation operation, ErrorCodes errorCode, String message,
                           UUID sessionId, UUID peekerId, UUID targetId) {
        super(errorCode, message);
        this.operation = operation;
        this.sessionId = sessionId;
        this.peekerId = peekerId;
        this.targetId = targetId;
    }
    
    public SessionOperation getOperation() {
        return operation;
    }
    
    public UUID getSessionId() {
        return sessionId;
    }
    
    public UUID getPeekerId() {
        return peekerId;
    }
    
    public UUID getTargetId() {
        return targetId;
    }
    
    public boolean hasSessionInfo() {
        return sessionId != null || peekerId != null || targetId != null;
    }
    
    @Override
    public String toString() {
        return String.format("SessionException{operation=%s, errorCode=%s, sessionId=%s, peekerId=%s, targetId=%s, message=%s}", 
            operation, getErrorCode(), sessionId, peekerId, targetId, getMessage());
    }
}