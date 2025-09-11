package com.peek.manager;

import com.peek.config.ModConfigManager;
import com.peek.manager.constants.ErrorCodes;
import com.peek.manager.exceptions.RequestException;
import com.peek.manager.constants.PeekConstants;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages peek invitations
 */
public class InviteManager extends BaseManager {
    
    // Map of inviter -> set of invitees with expiration times
    private final Map<UUID, Map<UUID, Long>> activeInvites = new ConcurrentHashMap<>();
    
    public InviteManager() {
        // Public constructor for dependency injection
    }
    
    /**
     * Create an invitation from inviter to invitee
     */
    public PeekConstants.Result<String> createInvite(ServerPlayerEntity inviter, ServerPlayerEntity invitee) {
        try {
            UUID inviterId = inviter.getUuid();
            UUID inviteeId = invitee.getUuid();
            
            // Check if invite already exists
            if (hasActiveInvite(inviterId, inviteeId)) {
                throw new RequestException(ErrorCodes.DUPLICATE_INVITE, Text.translatable("peek.error.duplicate_invite").getString());
            }
            
            long expirationTime = Instant.now().toEpochMilli() + 
                (ModConfigManager.getInviteExpirationSeconds() * 1000L);
            
            activeInvites.computeIfAbsent(inviterId, k -> new ConcurrentHashMap<>())
                .put(inviteeId, expirationTime);
                
            return PeekConstants.Result.success(Text.translatable("peek.message.invite_created").getString());
            
        } catch (RequestException e) {
            return PeekConstants.Result.failure(e.getMessage());
        } catch (Exception e) {
            return PeekConstants.Result.failure(Text.translatable("peek.message.invite_failed").getString());
        }
    }
    
    /**
     * Check if there's an active invite from inviter to invitee
     * Synchronized for data safety in low concurrency environment
     */
    public synchronized boolean hasActiveInvite(UUID inviterId, UUID inviteeId) {
        Map<UUID, Long> inviterInvites = activeInvites.get(inviterId);
        if (inviterInvites == null) return false;
        
        Long expiration = inviterInvites.get(inviteeId);
        if (expiration == null) return false;
        
        long currentTime = Instant.now().toEpochMilli();
        
        // Check if expired and cleanup if needed
        if (currentTime > expiration) {
            inviterInvites.remove(inviteeId);
            if (inviterInvites.isEmpty()) {
                activeInvites.remove(inviterId);
            }
            return false;
        }
        
        return true;
    }
    
    /**
     * Consume (remove) an invitation
     */
    public PeekConstants.Result<String> consumeInvite(UUID inviterId, UUID inviteeId) {
        try {
            Map<UUID, Long> inviterInvites = activeInvites.get(inviterId);
            if (inviterInvites != null) {
                boolean removed = inviterInvites.remove(inviteeId) != null;
                if (inviterInvites.isEmpty()) {
                    activeInvites.remove(inviterId);
                }
                
                if (removed) {
                    return PeekConstants.Result.success(Text.translatable("peek.message.invite_consumed").getString());
                } else {
                    throw new RequestException(ErrorCodes.INVITE_NOT_FOUND, Text.translatable("peek.error.invite_not_found").getString());
                }
            } else {
                throw new RequestException(ErrorCodes.INVITE_NOT_FOUND, Text.translatable("peek.error.invite_not_found").getString());
            }
        } catch (RequestException e) {
            return PeekConstants.Result.failure(e.getMessage());
        } catch (Exception e) {
            return PeekConstants.Result.failure(Text.translatable("peek.message.invite_consume_failed").getString());
        }
    }
    
    /**
     * Clean up expired invites
     */
    public void cleanupExpiredInvites() {
        long now = Instant.now().toEpochMilli();
        
        activeInvites.entrySet().removeIf(entry -> {
            Map<UUID, Long> invites = entry.getValue();
            invites.entrySet().removeIf(invite -> invite.getValue() <= now);
            return invites.isEmpty();
        });
    }
}