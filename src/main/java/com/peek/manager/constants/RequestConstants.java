package com.peek.manager.constants;

/**
 * Constants related to peek requests
 */
public final class RequestConstants {
    
    // Task types
    public static final String TASK_TYPE_AUTO_ACCEPT = "auto_accept";
    public static final String TASK_TYPE_EXPIRE_REQUEST = "expire_request";
    
    // Translation keys for buttons
    public static final String ACCEPT_BUTTON_TEXT = "peek.button.accept";
    public static final String DENY_BUTTON_TEXT = "peek.button.deny";
    
    private RequestConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}