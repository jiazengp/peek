package com.peek.manager.exceptions;

import com.peek.manager.constants.ErrorCodes;

/**
 * Base exception for all peek-related operations
 */
public class PeekException extends RuntimeException {
    private final String errorCode;
    private final String translationKey;
    private final Object[] messageArgs;
    
    public PeekException(String errorCode, String message) {
        this(errorCode, message, (Object[]) null);
    }
    
    public PeekException(String errorCode, String message, Object... messageArgs) {
        super(message);
        this.errorCode = errorCode;
        this.translationKey = null;
        this.messageArgs = messageArgs;
    }
    
    public PeekException(String errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, (Object[]) null);
    }
    
    public PeekException(String errorCode, String message, Throwable cause, Object... messageArgs) {
        super(message, cause);
        this.errorCode = errorCode;
        this.translationKey = null;
        this.messageArgs = messageArgs;
    }
    
    public PeekException(ErrorCodes errorCode, String message) {
        this(errorCode.name(), message, (Object[]) null);
        setTranslationKey(errorCode.getTranslationKey());
    }
    
    public PeekException(ErrorCodes errorCode, String message, Object... messageArgs) {
        super(message);
        this.errorCode = errorCode.name();
        this.translationKey = errorCode.getTranslationKey();
        this.messageArgs = messageArgs;
    }
    
    public PeekException(ErrorCodes errorCode, String message, Throwable cause) {
        this(errorCode.name(), message, cause, (Object[]) null);
        setTranslationKey(errorCode.getTranslationKey());
    }
    
    public PeekException(ErrorCodes errorCode, String message, Throwable cause, Object... messageArgs) {
        super(message, cause);
        this.errorCode = errorCode.name();
        this.translationKey = errorCode.getTranslationKey();
        this.messageArgs = messageArgs;
    }
    
    private String translationKeyField;
    
    private void setTranslationKey(String translationKey) {
        this.translationKeyField = translationKey;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getTranslationKey() {
        return translationKeyField != null ? translationKeyField : translationKey;
    }
    
    public Object[] getMessageArgs() {
        return messageArgs;
    }
    
    public boolean hasMessageArgs() {
        return messageArgs != null && messageArgs.length > 0;
    }
    
    /**
     * Gets a user-friendly error code for display
     */
    public String getDisplayErrorCode() {
        return errorCode != null ? errorCode.toLowerCase().replace("_", ".") : "unknown";
    }
    
    @Override
    public String toString() {
        return String.format("%s{errorCode=%s, message=%s}", 
            getClass().getSimpleName(), errorCode, getMessage());
    }
}