package com.peek.manager;

import net.minecraft.server.MinecraftServer;

/**
 * Abstract base class for all manager implementations.
 * Provides common functionality like server reference management and singleton pattern support.
 */
public abstract class BaseManager {
    
    // Use AtomicReference for lock-free server access
    private final java.util.concurrent.atomic.AtomicReference<MinecraftServer> serverRef = 
        new java.util.concurrent.atomic.AtomicReference<>();
    
    /**
     * Sets the server reference for this manager
     */
    public void setServer(MinecraftServer server) {
        serverRef.set(server);
    }
    
    /**
     * Gets the current server instance
     */
    protected MinecraftServer getCurrentServer() {
        return serverRef.get();
    }
    
    /**
     * Checks if server is available
     */
    protected boolean isServerAvailable() {
        return serverRef.get() != null;
    }
    
    /**
     * Cleanup method called during manager shutdown
     * Subclasses should override this to perform specific cleanup
     */
    public void shutdown() {
        // Default implementation - can be overridden
    }
}