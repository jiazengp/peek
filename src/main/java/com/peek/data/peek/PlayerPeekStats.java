package com.peek.data.peek;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Individual player peek statistics
 */
public record PlayerPeekStats(
    String playerName,
    long peekCount,           // Times this player peeked others
    long peekedCount,         // Times this player was peeked by others
    long totalPeekDuration,   // Total seconds spent peeking others
    long totalPeekedDuration, // Total seconds being peeked by others
    Instant firstPeekTime,
    Instant lastPeekTime,
    List<PeekHistoryEntry> recentHistory
) {
    
    public static final Codec<PlayerPeekStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("playerName").forGetter(PlayerPeekStats::playerName),
        Codec.LONG.optionalFieldOf("peekCount", 0L).forGetter(PlayerPeekStats::peekCount),
        Codec.LONG.optionalFieldOf("peekedCount", 0L).forGetter(PlayerPeekStats::peekedCount),
        Codec.LONG.optionalFieldOf("totalPeekDuration", 0L).forGetter(PlayerPeekStats::totalPeekDuration),
        Codec.LONG.optionalFieldOf("totalPeekedDuration", 0L).forGetter(PlayerPeekStats::totalPeekedDuration),
        Codec.STRING.xmap(Instant::parse, Instant::toString)
            .optionalFieldOf("firstPeekTime", Instant.now()).forGetter(PlayerPeekStats::firstPeekTime),
        Codec.STRING.xmap(Instant::parse, Instant::toString)
            .optionalFieldOf("lastPeekTime", Instant.now()).forGetter(PlayerPeekStats::lastPeekTime),
        Codec.list(PeekHistoryEntry.CODEC)
            .optionalFieldOf("recentHistory", new ArrayList<>()).forGetter(PlayerPeekStats::recentHistory)
    ).apply(instance, PlayerPeekStats::new));
    
    public static PlayerPeekStats createDefault(String playerName) {
        Instant now = Instant.now();
        return new PlayerPeekStats(playerName, 0L, 0L, 0L, 0L, now, now, new ArrayList<>());
    }
    
    public PlayerPeekStats incrementPeekCount() {
        return new PlayerPeekStats(
            playerName, peekCount + 1, peekedCount, totalPeekDuration, totalPeekedDuration,
            firstPeekTime, Instant.now(), recentHistory
        );
    }
    
    public PlayerPeekStats incrementPeekedCount() {
        return new PlayerPeekStats(
            playerName, peekCount, peekedCount + 1, totalPeekDuration, totalPeekedDuration,
            firstPeekTime, Instant.now(), recentHistory
        );
    }
    
    public PlayerPeekStats addPeekDuration(long seconds) {
        return new PlayerPeekStats(
            playerName, peekCount, peekedCount, totalPeekDuration + seconds, totalPeekedDuration,
            firstPeekTime, lastPeekTime, recentHistory
        );
    }
    
    public PlayerPeekStats addPeekedDuration(long seconds) {
        return new PlayerPeekStats(
            playerName, peekCount, peekedCount, totalPeekDuration, totalPeekedDuration + seconds,
            firstPeekTime, lastPeekTime, recentHistory
        );
    }
    
    public PlayerPeekStats addHistoryEntry(PeekHistoryEntry entry) {
        List<PeekHistoryEntry> newHistory = new ArrayList<>(recentHistory);
        newHistory.addFirst(entry); // Add to beginning
        
        // Keep only last 50 entries
        if (newHistory.size() > 50) {
            newHistory = newHistory.subList(0, 50);
        }
        
        return new PlayerPeekStats(
            playerName, peekCount, peekedCount, totalPeekDuration, totalPeekedDuration,
            firstPeekTime, lastPeekTime, newHistory
        );
    }
    
    /**
     * Gets average peek duration in seconds
     */
    public double getAveragePeekDuration() {
        return peekCount > 0 ? (double) totalPeekDuration / peekCount : 0.0;
    }
    
    /**
     * Gets average peeked duration in seconds
     */
    public double getAveragePeekedDuration() {
        return peekedCount > 0 ? (double) totalPeekedDuration / peekedCount : 0.0;
    }
    
    /**
     * Formats duration in minutes for display
     */
    public double getTotalPeekDurationMinutes() {
        return totalPeekDuration / 60.0;
    }
}