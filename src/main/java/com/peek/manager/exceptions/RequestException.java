package com.peek.manager.exceptions;

import com.peek.manager.constants.ErrorCodes;
import java.util.UUID;

/**
 * Exception thrown during peek request operations
 */
public class RequestException extends PeekException {
    private final UUID requestId;
    private final UUID requesterId;
    private final UUID targetId;
    private final RequestOperation operation;
    
    public enum RequestOperation {
        SEND,
        ACCEPT,
        DENY,
        CANCEL,
        EXPIRE,
        AUTO_ACCEPT,
        VALIDATE
    }
    
    public RequestException(ErrorCodes errorCode, String message) {
        this(RequestOperation.VALIDATE, errorCode, message, null, null, null);
    }

    public RequestException(ErrorCodes errorCode, String message, Throwable cause) {
        this(RequestOperation.VALIDATE, errorCode, message, cause, null, null, null);
    }
    
    public RequestException(RequestOperation operation, ErrorCodes errorCode, String message) {
        this(operation, errorCode, message, null, null, null);
    }
    
    public RequestException(RequestOperation operation, ErrorCodes errorCode, String message,
                           UUID requestId, UUID requesterId, UUID targetId) {
        super(errorCode, message);
        this.operation = operation;
        this.requestId = requestId;
        this.requesterId = requesterId;
        this.targetId = targetId;
    }
    
    public RequestException(RequestOperation operation, ErrorCodes errorCode, String message, Throwable cause) {
        this(operation, errorCode, message, cause, null, null, null);
    }
    
    public RequestException(RequestOperation operation, ErrorCodes errorCode, String message, Throwable cause,
                           UUID requestId, UUID requesterId, UUID targetId) {
        super(errorCode, message, cause);
        this.operation = operation;
        this.requestId = requestId;
        this.requesterId = requesterId;
        this.targetId = targetId;
    }
    
    public RequestOperation getOperation() {
        return operation;
    }
    
    public UUID getRequestId() {
        return requestId;
    }
    
    public UUID getRequesterId() {
        return requesterId;
    }
    
    public UUID getTargetId() {
        return targetId;
    }
    
    public boolean hasRequestInfo() {
        return requestId != null || requesterId != null || targetId != null;
    }
    
    /**
     * @deprecated Use getErrorCode() from parent PeekException instead
     */
    @Deprecated
    public ErrorCodes getErrorCodeEnum() {
        try {
            return ErrorCodes.valueOf(getErrorCode());
        } catch (IllegalArgumentException e) {
            return ErrorCodes.INTERNAL_ERROR;
        }
    }
    
    @Override
    public String toString() {
        return String.format("RequestException{operation=%s, errorCode=%s, requestId=%s, requesterId=%s, targetId=%s, message=%s}", 
            operation, getErrorCode(), requestId, requesterId, targetId, getMessage());
    }
}