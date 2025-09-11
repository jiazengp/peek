package com.peek.data.peek;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.time.Instant;
import java.util.*;

/**
 * Stores global peek statistics and individual player statistics
 */
public record PeekStatistics(
    Map<UUID, PlayerPeekStats> playerStats,
    long totalPeekSessions,
    long totalPeekDuration,
    Instant lastUpdated
) {
    
    public static final Codec<PeekStatistics> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(
            Codec.STRING.xmap(UUID::fromString, UUID::toString),
            PlayerPeekStats.CODEC
        ).optionalFieldOf("playerStats", new HashMap<>()).forGetter(PeekStatistics::playerStats),
        Codec.LONG.optionalFieldOf("totalPeekSessions", 0L).forGetter(PeekStatistics::totalPeekSessions),
        Codec.LONG.optionalFieldOf("totalPeekDuration", 0L).forGetter(PeekStatistics::totalPeekDuration),
        Codec.STRING.xmap(Instant::parse, Instant::toString)
            .optionalFieldOf("lastUpdated", Instant.now()).forGetter(PeekStatistics::lastUpdated)
    ).apply(instance, PeekStatistics::new));
    
    public static PeekStatistics createDefault() {
        return new PeekStatistics(new HashMap<>(), 0L, 0L, Instant.now());
    }
    
    /**
     * Records a peek session
     */
    public PeekStatistics recordPeekSession(UUID peekerId, String peekerName, UUID targetId, String targetName, long durationSeconds) {
        Map<UUID, PlayerPeekStats> newPlayerStats = new HashMap<>(playerStats);
        
        // Update peeker stats
        PlayerPeekStats peekerStats = newPlayerStats.getOrDefault(peekerId, PlayerPeekStats.createDefault(peekerName));
        peekerStats = peekerStats.incrementPeekCount().addPeekDuration(durationSeconds);
        newPlayerStats.put(peekerId, peekerStats);
        
        // Update target stats
        PlayerPeekStats targetStats = newPlayerStats.getOrDefault(targetId, PlayerPeekStats.createDefault(targetName));
        targetStats = targetStats.incrementPeekedCount().addPeekedDuration(durationSeconds);
        newPlayerStats.put(targetId, targetStats);
        
        return new PeekStatistics(
            newPlayerStats,
            totalPeekSessions + 1,
            totalPeekDuration + durationSeconds,
            Instant.now()
        );
    }
    
    /**
     * Gets player stats, returns empty stats if player not found
     */
    public PlayerPeekStats getPlayerStats(UUID playerId, String playerName) {
        return playerStats.getOrDefault(playerId, PlayerPeekStats.createDefault(playerName));
    }
    
    /**
     * Gets all players sorted by peek count (descending)
     */
    public List<Map.Entry<UUID, PlayerPeekStats>> getTopPeekers() {
        return playerStats.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().peekCount(), a.getValue().peekCount()))
            .toList();
    }
    
    /**
     * Gets all players sorted by peeked count (descending)  
     */
    public List<Map.Entry<UUID, PlayerPeekStats>> getMostPeeked() {
        return playerStats.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().peekedCount(), a.getValue().peekedCount()))
            .toList();
    }
    
    /**
     * Gets all players sorted by total duration (descending)
     */
    public List<Map.Entry<UUID, PlayerPeekStats>> getTopByDuration() {
        return playerStats.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().totalPeekDuration(), a.getValue().totalPeekDuration()))
            .toList();
    }
    
    /**
     * Gets paginated results
     */
    public List<Map.Entry<UUID, PlayerPeekStats>> getPage(List<Map.Entry<UUID, PlayerPeekStats>> sortedList, int page, int pageSize) {
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, sortedList.size());
        
        if (startIndex >= sortedList.size()) {
            return new ArrayList<>();
        }
        
        return sortedList.subList(startIndex, endIndex);
    }
    
    /**
     * Gets total pages for pagination
     */
    public int getTotalPages(int pageSize) {
        return (int) Math.ceil((double) playerStats.size() / pageSize);
    }
}