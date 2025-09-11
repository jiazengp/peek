package com.peek.data.peek;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a peek request from one player to another
 */
public class PeekRequest {
    private final UUID id;
    private final UUID requesterId;
    private final UUID targetId;
    private final String requesterName;
    private final String targetName;
    private final Instant createdAt;
    private final Instant expiresAt;
    private RequestStatus status;
    
    public enum RequestStatus {
        PENDING,
        ACCEPTED,
        DENIED,
        EXPIRED,
        CANCELLED
    }
    
    public PeekRequest(UUID requesterId, UUID targetId, String requesterName, String targetName, long timeoutSeconds) {
        this.id = UUID.randomUUID();
        this.requesterId = requesterId;
        this.targetId = targetId;
        this.requesterName = requesterName;
        this.targetName = targetName;
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plusSeconds(timeoutSeconds);
        this.status = RequestStatus.PENDING;
    }
    
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt) && status == RequestStatus.PENDING;
    }
    
    public long getRemainingSeconds() {
        if (isExpired()) return 0;
        return Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
    }
    
    // Getters
    public UUID getId() { return id; }
    public UUID getRequesterId() { return requesterId; }
    public UUID getTargetId() { return targetId; }
    public String getRequesterName() { return requesterName; }
    public String getTargetName() { return targetName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public RequestStatus getStatus() { return status; }
    
    // Status management
    public void setStatus(RequestStatus status) { this.status = status; }
    
    public boolean canAccept() { return status == RequestStatus.PENDING && !isExpired(); }
    public boolean canDeny() { return status == RequestStatus.PENDING && !isExpired(); }
    public boolean canCancel() { return status == RequestStatus.PENDING; }
}