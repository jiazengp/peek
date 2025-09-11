package com.peek.manager;

import net.minecraft.server.MinecraftServer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Dependency injection container for all Manager instances.
 * Replaces singleton pattern with controlled instance management.
 */
public class ManagerRegistry {
    private static volatile ManagerRegistry instance;
    private final Map<Class<?>, Object> managers = new ConcurrentHashMap<>();
    private volatile MinecraftServer server;
    
    private ManagerRegistry() {
        // Managers will be initialized explicitly when first accessed
    }
    
    public static ManagerRegistry getInstance() {
        if (instance == null) {
            synchronized (ManagerRegistry.class) {
                if (instance == null) {
                    instance = new ManagerRegistry();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize all manager instances with dependency injection
     */
    private void initializeManagers() {
        // Create managers without dependencies first
        InviteManager inviteManager = new InviteManager();
        PlayerStateManager playerStateManager = new PlayerStateManager();
        PeekStatisticsManager statisticsManager = new PeekStatisticsManager();
        PeekRequestManager requestManager = new PeekRequestManager();
        
        // Register basic managers first
        managers.put(InviteManager.class, inviteManager);
        managers.put(PlayerStateManager.class, playerStateManager);
        managers.put(PeekStatisticsManager.class, statisticsManager);
        managers.put(PeekRequestManager.class, requestManager);
        
        // Create managers with dependencies using already registered managers
        PeekSessionManager sessionManager = new PeekSessionManager(requestManager, playerStateManager);
        managers.put(PeekSessionManager.class, sessionManager);
    }
    
    /**
     * Register a manager instance
     */
    public <T> void registerManager(Class<T> managerClass, T manager) {
        managers.put(managerClass, manager);
    }
    
    /**
     * Get manager instance by type
     */
    @SuppressWarnings("unchecked")
    public <T> T getManager(Class<T> managerClass) {
        T manager = (T) managers.get(managerClass);
        if (manager == null) {
            // Lazy initialization if not yet initialized
            synchronized (this) {
                manager = (T) managers.get(managerClass);
                if (manager == null) {
                    initializeManagers();
                    manager = (T) managers.get(managerClass);
                    if (manager == null) {
                        throw new IllegalArgumentException("Manager not registered: " + managerClass.getSimpleName());
                    }
                }
            }
        }
        return manager;
    }
    
    /**
     * Set server for all managers
     */
    public void setServer(MinecraftServer server) {
        this.server = server;
        // Update server reference for all managers
        managers.values().forEach(manager -> {
            if (manager instanceof BaseManager) {
                ((BaseManager) manager).setServer(server);
            }
        });
    }
    
    /**
     * Shutdown all managers
     */
    public void shutdown() {
        managers.values().forEach(manager -> {
            if (manager instanceof BaseManager) {
                ((BaseManager) manager).shutdown();
            }
        });
        managers.clear();
    }
    
    /**
     * For testing: replace manager instance
     */
    public <T> void replaceManager(Class<T> managerClass, T mockManager) {
        managers.put(managerClass, mockManager);
    }
}