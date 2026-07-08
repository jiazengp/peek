package com.peek.data.peek;

import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an active peek session where one player is viewing another's perspective
 */
public class PeekSession {
    private final UUID id;
    private final UUID peekerId;
    private final UUID targetId;
    private final String peekerName;
    private final String targetName;
    private final Instant startTime;
    private Instant lastUpdateTime;
    
    // Original states for restoration
    private final PlayerState originalPeekerState;
    private final UUID originalWorldId;
    
    // Current session state
    private boolean isActive;
    private Vec3 lastKnownTargetPosition;
    private UUID currentWorldId;
    private Vec3 lastKnownPeekerPosition;  // Track peeker position for distance check
    
    public PeekSession(UUID peekerId, UUID targetId, String peekerName, String targetName, PlayerState originalState, UUID worldId) {
        this.id = UUID.randomUUID();
        this.peekerId = peekerId;
        this.targetId = targetId;
        this.peekerName = peekerName;
        this.targetName = targetName;
        this.startTime = Instant.now();
        this.lastUpdateTime = startTime;
        this.originalPeekerState = originalState;
        this.originalWorldId = worldId;
        this.currentWorldId = worldId;
        this.isActive = true;
    }
    
    public void updateTargetPosition(Vec3 position, UUID worldId) {
        this.lastKnownTargetPosition = position;
        this.currentWorldId = worldId;
        this.lastUpdateTime = Instant.now();
    }
    
    public void updatePeekerPosition(Vec3 position) {
        this.lastKnownPeekerPosition = position;
        this.lastUpdateTime = Instant.now();
    }
    
    public double getDistanceFromTarget() {
        if (lastKnownTargetPosition == null || lastKnownPeekerPosition == null) {
            return 0.0;
        }
        return lastKnownTargetPosition.distanceTo(lastKnownPeekerPosition);
    }
    
    public void markInactive() {
        this.isActive = false;
    }
    
    public long getDurationSeconds() {
        Instant endTime = isActive ? Instant.now() : lastUpdateTime;
        return endTime.getEpochSecond() - startTime.getEpochSecond();
    }
    
    public boolean hasCrossedDimension() {
        return !originalWorldId.equals(currentWorldId);
    }
    
    // Getters
    public UUID getId() { return id; }
    public UUID getPeekerId() { return peekerId; }
    public UUID getTargetId() { return targetId; }
    public String getPeekerName() { return peekerName; }
    public String getTargetName() { return targetName; }
    public Instant getStartTime() { return startTime; }
    public Instant getLastUpdateTime() { return lastUpdateTime; }
    public PlayerState getOriginalPeekerState() { return originalPeekerState; }
    public UUID getOriginalWorldId() { return originalWorldId; }
    public boolean isActive() { return isActive; }
    public Vec3 getLastKnownTargetPosition() { return lastKnownTargetPosition; }
    public UUID getCurrentWorldId() { return currentWorldId; }
    public Vec3 getLastKnownPeekerPosition() { return lastKnownPeekerPosition; }
}

