package com.peek.utils;

import com.peek.PeekMod;
import com.peek.manager.constants.ErrorCodes;
import com.peek.manager.constants.PeekConstants;
import com.peek.manager.exceptions.PeekException;
import com.peek.manager.exceptions.RequestException;
import com.peek.manager.exceptions.SessionException;
import com.peek.manager.exceptions.TeleportationException;
import com.peek.manager.exceptions.ValidationException;

/**
 * Standardized exception handling utility for consistent error management
 */
public class ExceptionHandler {
    
    /**
     * Handle and log request-related exceptions
     */
    public static PeekConstants.Result<String> handleRequestException(Exception e, String operation, Object... context) {
        if (e instanceof RequestException re) {
            logStructuredException("REQUEST", re.getOperation().toString(), re.getErrorCode(), re.getMessage(), context);
            return PeekConstants.Result.failure(re.getTranslationKey() != null ? re.getTranslationKey() : re.getMessage());
        } else if (e instanceof PeekException pe) {
            logStructuredException("REQUEST", operation, pe.getErrorCode(), pe.getMessage(), context);
            return PeekConstants.Result.failure(pe.getTranslationKey() != null ? pe.getTranslationKey() : pe.getMessage());
        } else {
            logUnstructuredException("REQUEST", operation, e, context);
            return PeekConstants.Result.failure(ErrorCodes.INTERNAL_ERROR);
        }
    }
    
    /**
     * Handle and log session-related exceptions
     */
    public static PeekConstants.Result<String> handleSessionException(Exception e, String operation, Object... context) {
        if (e instanceof SessionException se) {
            logStructuredException("SESSION", se.getOperation().toString(), se.getErrorCode(), se.getMessage(), context);
            return PeekConstants.Result.failure(se.getTranslationKey() != null ? se.getTranslationKey() : se.getMessage());
        } else if (e instanceof PeekException pe) {
            logStructuredException("SESSION", operation, pe.getErrorCode(), pe.getMessage(), context);
            return PeekConstants.Result.failure(pe.getTranslationKey() != null ? pe.getTranslationKey() : pe.getMessage());
        } else {
            logUnstructuredException("SESSION", operation, e, context);
            return PeekConstants.Result.failure(ErrorCodes.INTERNAL_ERROR);
        }
    }
    
    /**
     * Handle and log teleportation-related exceptions
     */
    public static PeekConstants.Result<String> handleTeleportationException(Exception e, String operation, Object... context) {
        if (e instanceof TeleportationException te) {
            logStructuredException("TELEPORT", te.getOperation().toString(), te.getErrorCode(), te.getMessage(), context);
            return PeekConstants.Result.failure(te.getTranslationKey() != null ? te.getTranslationKey() : te.getMessage());
        } else if (e instanceof PeekException pe) {
            logStructuredException("TELEPORT", operation, pe.getErrorCode(), pe.getMessage(), context);
            return PeekConstants.Result.failure(pe.getTranslationKey() != null ? pe.getTranslationKey() : pe.getMessage());
        } else {
            logUnstructuredException("TELEPORT", operation, e, context);
            return PeekConstants.Result.failure(ErrorCodes.TELEPORT_FAILED);
        }
    }
    
    /**
     * Handle and log validation-related exceptions
     */
    public static PeekConstants.Result<String> handleValidationException(Exception e, String operation, Object... context) {
        if (e instanceof ValidationException ve) {
            logStructuredException("VALIDATION", ve.getValidationType().toString(), ve.getErrorCode(), ve.getMessage(), context);
            return PeekConstants.Result.failure(ve.getTranslationKey() != null ? ve.getTranslationKey() : ve.getMessage());
        } else if (e instanceof PeekException pe) {
            logStructuredException("VALIDATION", operation, pe.getErrorCode(), pe.getMessage(), context);
            return PeekConstants.Result.failure(pe.getTranslationKey() != null ? pe.getTranslationKey() : pe.getMessage());
        } else {
            logUnstructuredException("VALIDATION", operation, e, context);
            return PeekConstants.Result.failure(ErrorCodes.VALIDATION_FAILED);
        }
    }
    
    /**
     * Log structured exception with consistent format
     */
    private static void logStructuredException(String category, String operation, String errorCode, String message, Object... context) {
        String contextStr = formatContext(context);
        PeekMod.LOGGER.error("[{}] {} failed - Code: {}, Message: {}{}", 
            category, operation, errorCode, message, contextStr);
    }
    
    /**
     * Log unstructured exception with full stack trace
     */
    private static void logUnstructuredException(String category, String operation, Exception e, Object... context) {
        String contextStr = formatContext(context);
        PeekMod.LOGGER.error("[{}] {} failed with unexpected exception{}", 
            category, operation, contextStr, e);
    }
    
    /**
     * Format context information for logging
     */
    private static String formatContext(Object... context) {
        if (context == null || context.length == 0) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder(" (Context: ");
        for (int i = 0; i < context.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(context[i]);
        }
        sb.append(")");
        return sb.toString();
    }
    
    /**
     * Create standardized error message with context
     */
    public static String createErrorMessage(String operation, String errorCode, Object... context) {
        String contextStr = formatContext(context);
        return String.format("%s failed: %s%s", operation, errorCode, contextStr);
    }
    
    /**
     * Wrap any exception as appropriate PeekException
     */
    public static PeekException wrapException(Exception e, String errorCode, String operation) {
        if (e instanceof PeekException) {
            return (PeekException) e;
        }
        return new PeekException(errorCode, operation + " failed: " + e.getMessage(), e);
    }
}