package com.peek.manager;

import com.peek.PeekMod;
import com.peek.config.ModConfigManager;
import com.peek.data.GlobalDataStorage;
import com.peek.data.peek.PeekHistoryEntry;
import com.peek.data.peek.PeekStatistics;
import com.peek.data.peek.PlayerPeekStats;
import com.peek.manager.constants.SessionConstants;
import com.peek.manager.constants.PeekConstants;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages peek statistics and history
 */
public class PeekStatisticsManager extends BaseManager {
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    // server is now inherited from BaseManager
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, SessionConstants.STATS_CLEANUP_THREAD_NAME);
        t.setDaemon(true); // Daemon thread so it won't prevent JVM shutdown
        return t;
    });

    public PeekStatisticsManager() {
        startCacheCleanupTask();
    }
    
    /**
     * Records a completed peek session
     */
    public void recordPeekSession(MinecraftServer server, UUID peekerId, String peekerName, UUID targetId, String targetName, 
                                 long durationSeconds, boolean crossedDimension) {
        try {
            PeekMod.LOGGER.debug("Recording peek session: {} -> {} ({}s, crossed: {})", 
                peekerName, targetName, durationSeconds, crossedDimension);
            
            // Synchronize only the critical section
            PeekStatistics updatedStats;
            synchronized (this) {
                // Load current statistics
                PeekStatistics currentStats = loadStatistics(server);
                
                // Record the session
                updatedStats = currentStats.recordPeekSession(peekerId, peekerName, targetId, targetName, durationSeconds);
            }
            
            // Save and cache operations outside synchronized block
            saveStatistics(server, updatedStats);
            clearCache();
            
                        
        } catch (Exception e) {
            PeekMod.LOGGER.error("Error recording peek session", e);
        }
    }
    
    /**
     * Gets player statistics with caching (server-aware version)
     */
    public PlayerPeekStats getPlayerStats(MinecraftServer server, UUID playerId, String playerName) {
        String cacheKey = SessionConstants.PLAYER_STATS_CACHE_KEY_PREFIX + playerId.toString();
        
        // Check cache first
        PlayerPeekStats cached = (PlayerPeekStats) cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Load from global statistics
        PeekStatistics globalStats = loadStatistics(server);
        PlayerPeekStats stats = globalStats.getPlayerStats(playerId, playerName);
        
        // Cache the result
        cache.put(cacheKey, stats);
        
        // Schedule cache removal
        scheduler.schedule(() -> cache.remove(cacheKey), 
            ModConfigManager.getStatsCacheTtlMinutes(), TimeUnit.MINUTES);
        
        return stats;
    }
    
    /**
     * Gets player statistics with caching (fallback version without server)
     */
    public PlayerPeekStats getPlayerStats(UUID playerId, String playerName) {
        return getPlayerStats(getCurrentServer(), playerId, playerName);
    }
    
    /**
     * Gets global statistics
     */
    public PeekStatistics getGlobalStatistics() {
        String cacheKey = SessionConstants.GLOBAL_STATS_CACHE_KEY;
        
        PeekStatistics cached = (PeekStatistics) cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Try to get current server for loading data
        MinecraftServer server = getCurrentServer();
        PeekStatistics stats = loadStatistics(server);
        
        // Cache the result
        cache.put(cacheKey, stats);
        
        // Schedule cache removal
        scheduler.schedule(() -> cache.remove(cacheKey), 
            ModConfigManager.getStatsCacheTtlMinutes(), TimeUnit.MINUTES);
        
        return stats;
    }
    
    /**
     * Gets top players by peek count with pagination
     */
    public List<Map.Entry<UUID, PlayerPeekStats>> getTopPeekers(int page, int pageSize) {
        PeekStatistics stats = getGlobalStatistics();
        List<Map.Entry<UUID, PlayerPeekStats>> sorted = stats.getTopPeekers();
        return stats.getPage(sorted, page, pageSize);
    }
    
    /**
     * Gets most peeked players with pagination
     */
    public List<Map.Entry<UUID, PlayerPeekStats>> getMostPeeked(int page, int pageSize) {
        PeekStatistics stats = getGlobalStatistics();
        List<Map.Entry<UUID, PlayerPeekStats>> sorted = stats.getMostPeeked();
        return stats.getPage(sorted, page, pageSize);
    }
    
    /**
     * Gets top players by duration with pagination
     */
    public List<Map.Entry<UUID, PlayerPeekStats>> getTopByDuration(int page, int pageSize) {
        PeekStatistics stats = getGlobalStatistics();
        List<Map.Entry<UUID, PlayerPeekStats>> sorted = stats.getTopByDuration();
        return stats.getPage(sorted, page, pageSize);
    }
    
    /**
     * Gets sorted players by specified sort type
     */
    public List<Map.Entry<UUID, PlayerPeekStats>> getSortedPlayers(PeekConstants.SortType sortType, int page, int pageSize) {
        return switch (sortType) {
            case PEEK_COUNT -> getTopPeekers(page, pageSize);
            case PEEKED_COUNT -> getMostPeeked(page, pageSize);
            case TOTAL_DURATION -> getTopByDuration(page, pageSize);
            case LAST_ACTIVITY -> getTopPeekers(page, pageSize); // Could implement separate sorting
            case PLAYER_NAME -> getTopPeekers(page, pageSize); // Could implement name-based sorting
        };
    }
    
    /**
     * Gets total pages for pagination
     */
    public int getTotalPages(int pageSize) {
        return getGlobalStatistics().getTotalPages(pageSize);
    }
    
    /**
     * Gets player history entries
     */
    public List<PeekHistoryEntry> getPlayerHistory(UUID playerId, String playerName) {
        PlayerPeekStats stats = getPlayerStats(playerId, playerName);
        return stats.recentHistory();
    }
    
    /**
     * Searches for players by name (partial matching)
     */
    public List<Map.Entry<UUID, PlayerPeekStats>> searchPlayersByName(String namePattern, int page, int pageSize) {
        PeekStatistics stats = getGlobalStatistics();
        String pattern = namePattern.toLowerCase();
        
        List<Map.Entry<UUID, PlayerPeekStats>> filtered = stats.playerStats().entrySet().stream()
            .filter(entry -> entry.getValue().playerName().toLowerCase().contains(pattern))
            .sorted((a, b) -> a.getValue().playerName().compareToIgnoreCase(b.getValue().playerName()))
            .toList();
        
        return stats.getPage(filtered, page, pageSize);
    }
    
    /**
     * Gets summary statistics for admin overview
     */
    public Map<String, Object> getSummaryStats() {
        PeekStatistics stats = getGlobalStatistics();
        
        Map<String, Object> summary = new ConcurrentHashMap<>();
        summary.put("totalSessions", stats.totalPeekSessions());
        summary.put("totalDuration", stats.totalPeekDuration());
        summary.put("totalPlayers", stats.playerStats().size());
        summary.put("averageSessionDuration", 
            stats.totalPeekSessions() > 0 ? (double) stats.totalPeekDuration() / stats.totalPeekSessions() : 0.0);
        
        // Get top peeker
        List<Map.Entry<UUID, PlayerPeekStats>> topPeekers = getTopPeekers(0, 1);
        if (!topPeekers.isEmpty()) {
            summary.put("topPeeker", topPeekers.getFirst().getValue().playerName());
            summary.put("topPeekerCount", topPeekers.getFirst().getValue().peekCount());
        }
        
        // Get most peeked
        List<Map.Entry<UUID, PlayerPeekStats>> mostPeeked = getMostPeeked(0, 1);
        if (!mostPeeked.isEmpty()) {
            summary.put("mostPeeked", mostPeeked.getFirst().getValue().playerName());
            summary.put("mostPeekedCount", mostPeeked.getFirst().getValue().peekedCount());
        }
        
        return summary;
    }
    
    /**
     * Clears cached statistics (for reload)
     */
    public void clearCache() {
        cache.clear();
        PeekMod.LOGGER.info("Cleared peek statistics cache");
    }
    
    /**
     * Performs cleanup of old data
     */
    public void performCleanup() {
        // This would implement cleanup of old history entries, etc.
        PeekMod.LOGGER.info("Performing peek statistics cleanup");
        
        // Clear cache as part of cleanup
        clearCache();
    }
    
    /**
     * Gets statistics for a specific time period
     */
    public Optional<Map<String, Object>> getStatsForPeriod(String period) {
        // This would implement time-based filtering
        // For now, return empty
        return Optional.empty();
    }
    
    /**
     * Exports statistics data (for backup/analysis)
     */
    public Map<String, Object> exportStatistics() {
        PeekStatistics stats = getGlobalStatistics();
        
        Map<String, Object> export = new ConcurrentHashMap<>();
        export.put("statistics", stats);
        export.put("exportTime", java.time.Instant.now().toString());
        export.put("version", PeekMod.class.getPackage().getImplementationVersion());
        
        return export;
    }
    
    private void startCacheCleanupTask() {
        // Clean up expired cache entries every 10 minutes
        scheduler.scheduleAtFixedRate(() -> {
            // Cache cleanup is handled automatically by scheduled tasks when items are added
            // This is just a periodic cleanup for any missed entries
            if (cache.size() > 100) { // If cache grows too large
                PeekMod.LOGGER.debug("Cache size: {}, considering cleanup", cache.size());
            }
        }, 10, 10, TimeUnit.MINUTES);
    }
    
    public void saveAndShutdown() {
        // Save any pending statistics updates
        try {
            PeekMod.LOGGER.info("Saving peek statistics...");
            
            // Force save current cached statistics
            MinecraftServer server = getCurrentServer();
            if (server != null) {
                PeekStatistics cachedStats = (PeekStatistics) cache.get(SessionConstants.GLOBAL_STATS_CACHE_KEY);
                if (cachedStats != null) {
                    saveStatistics(server, cachedStats);
                }
            }
            
        } catch (Exception e) {
            PeekMod.LOGGER.error("Error saving peek statistics", e);
        } finally {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            cache.clear();
        }
    }
    
    // Private helper methods
    
    private PeekStatistics loadStatistics(MinecraftServer server) {
        if (server == null) {
            PeekMod.LOGGER.debug("No server available, returning default statistics");
            return PeekStatistics.createDefault();
        }
        
        try {
            PeekStatistics stats = GlobalDataStorage.PEEK_STATISTICS_STORAGE.load(server);
            return stats != null ? stats : PeekStatistics.createDefault();
        } catch (Exception e) {
            PeekMod.LOGGER.error("Error loading peek statistics, using defaults", e);
            return PeekStatistics.createDefault();
        }
    }
    
    private void saveStatistics(MinecraftServer server, PeekStatistics statistics) {
        if (server == null) {
            PeekMod.LOGGER.warn("No server available, cannot save statistics");
            return;
        }
        
        try {
            GlobalDataStorage.PEEK_STATISTICS_STORAGE.save(server, statistics);
            PeekMod.LOGGER.debug("Successfully saved peek statistics");
        } catch (Exception e) {
            PeekMod.LOGGER.error("Error saving peek statistics", e);
        }
    }
    
    // setServer() and getCurrentServer() are now inherited from BaseManager
}