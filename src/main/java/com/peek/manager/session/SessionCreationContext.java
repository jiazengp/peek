package com.peek.manager.session;

import com.peek.data.peek.PeekSession;
import com.peek.data.peek.PlayerState;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Objects;
import java.util.UUID;

/**
 * Encapsulates the state and context during peek session creation.
 * Reduces parameter passing between session creation methods.
 */
public class SessionCreationContext {
    private final ServerPlayerEntity peeker;
    private final ServerPlayerEntity target;
    private final UUID peekerId;
    private final UUID targetId;
    
    // Session switching context
    private boolean wasSwitching = false;
    private PlayerState existingOriginalState = null;
    private PeekSession currentSessionToRestore = null;
    private UUID originalWorldId = null;
    
    // Creation result
    private PeekSession createdSession = null;
    private String errorMessage = null;
    
    public SessionCreationContext(ServerPlayerEntity peeker, ServerPlayerEntity target) {
        this.peeker = peeker;
        this.target = target;
        this.peekerId = peeker.getUuid();
        this.targetId = target.getUuid();
    }
    
    // Getters
    public ServerPlayerEntity getPeeker() { return peeker; }
    public ServerPlayerEntity getTarget() { return target; }
    public UUID getPeekerId() { return peekerId; }
    public UUID getTargetId() { return targetId; }
    
    // Session switching context
    public boolean isWasSwitching() { return wasSwitching; }
    public void setWasSwitching(boolean wasSwitching) { this.wasSwitching = wasSwitching; }
    
    public PlayerState getExistingOriginalState() { return existingOriginalState; }
    public void setExistingOriginalState(PlayerState existingOriginalState) { this.existingOriginalState = existingOriginalState; }
    
    public PeekSession getCurrentSessionToRestore() { return currentSessionToRestore; }
    public void setCurrentSessionToRestore(PeekSession currentSessionToRestore) { this.currentSessionToRestore = currentSessionToRestore; }
    
    public UUID getOriginalWorldId() { return originalWorldId; }
    public void setOriginalWorldId(UUID originalWorldId) { this.originalWorldId = originalWorldId; }
    
    // Result context
    public PeekSession getCreatedSession() { return createdSession; }
    public void setCreatedSession(PeekSession createdSession) { this.createdSession = createdSession; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public boolean hasError() { return errorMessage != null; }
    public boolean isSuccessful() { return createdSession != null && errorMessage == null; }
    
    /**
     * Determine the original state to use (existing from switching or newly captured)
     */
    public PlayerState determineOriginalState() {
        return Objects.requireNonNullElseGet(existingOriginalState, () -> {
            var registryManager = com.peek.utils.compat.PlayerCompat.getRegistryManager(peeker);
            if (registryManager == null) {
                throw new IllegalStateException("Cannot capture player state - server or registry manager not available");
            }
            return PlayerState.capture(peeker, registryManager);
        });
    }
    
    /**
     * Determine the original world ID to use
     */
    public UUID determineOriginalWorldId() {
        if (existingOriginalState != null) {
            return existingOriginalState.worldId();
        } else {
            return UUID.nameUUIDFromBytes(
                peeker.getWorld().getRegistryKey().getValue().toString().getBytes()
            );
        }
    }
}