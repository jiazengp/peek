package com.peek.data.peek;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single entry in a player's peek history
 */
public record PeekHistoryEntry(
    UUID sessionId,
    UUID otherPlayerId,
    String otherPlayerName,
    PeekType type,
    Instant timestamp,
    long durationSeconds,
    boolean crossedDimension
) {
    
    public enum PeekType {
        PEEKED_OTHER,    // This player peeked someone else
        WAS_PEEKED       // This player was peeked by someone else
    }
    
    public static final Codec<PeekHistoryEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("sessionId").forGetter(PeekHistoryEntry::sessionId),
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("otherPlayerId").forGetter(PeekHistoryEntry::otherPlayerId),
        Codec.STRING.fieldOf("otherPlayerName").forGetter(PeekHistoryEntry::otherPlayerName),
        Codec.STRING.xmap(PeekType::valueOf, PeekType::name).fieldOf("type").forGetter(PeekHistoryEntry::type),
        Codec.STRING.xmap(Instant::parse, Instant::toString).fieldOf("timestamp").forGetter(PeekHistoryEntry::timestamp),
        Codec.LONG.fieldOf("durationSeconds").forGetter(PeekHistoryEntry::durationSeconds),
        Codec.BOOL.optionalFieldOf("crossedDimension", false).forGetter(PeekHistoryEntry::crossedDimension)
    ).apply(instance, PeekHistoryEntry::new));
    
    public static PeekHistoryEntry createPeekedOther(UUID sessionId, UUID targetId, String targetName, long duration, boolean crossedDimension) {
        return new PeekHistoryEntry(sessionId, targetId, targetName, PeekType.PEEKED_OTHER, Instant.now(), duration, crossedDimension);
    }
    
    public static PeekHistoryEntry createWasPeeked(UUID sessionId, UUID peekerId, String peekerName, long duration, boolean crossedDimension) {
        return new PeekHistoryEntry(sessionId, peekerId, peekerName, PeekType.WAS_PEEKED, Instant.now(), duration, crossedDimension);
    }

    /**
     * Checks if this entry is recent (within last 24 hours)
     */
    public boolean isRecent() {
        return timestamp.isAfter(Instant.now().minusSeconds(24 * 60 * 60));
    }
}