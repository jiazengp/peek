package com.peek.manager;

import com.peek.PeekMod;
import com.peek.config.ModConfigManager;
import com.peek.manager.constants.ErrorCodes;
import com.peek.manager.constants.GameConstants;
import com.peek.data.peek.PeekRequest;
import com.peek.data.peek.PeekSession;
import com.peek.utils.CommandUtils;
import com.peek.utils.CooldownManager;
import com.peek.manager.constants.PeekConstants;
import com.peek.utils.RequestUtils;
import com.peek.utils.TickTaskManager;
import com.peek.utils.LoggingHelper;
import com.peek.manager.request.NotificationHandler;
import com.peek.manager.constants.RequestConstants;
import com.peek.utils.compat.ProfileCompat;
import com.peek.utils.compat.ServerPlayerCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages peek requests between players
 */
public class PeekRequestManager extends BaseManager {
    private final Map<UUID, PeekRequest> activeRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerRequestCounts = new ConcurrentHashMap<>();
    private final CooldownManager cooldownManager = CooldownManager.getInstance();
    
    // Performance optimization: index for faster lookups
    private final Map<UUID, UUID> targetToRequestId = new ConcurrentHashMap<>(); // target -> request id
    private final Map<UUID, UUID> requesterToRequestId = new ConcurrentHashMap<>(); // requester -> request id
    
    // Separated components for better architecture  
    private final NotificationHandler notificationHandler = new NotificationHandler();
    
    // Unified tick-based task management
    private final TickTaskManager tickTaskManager = new TickTaskManager();
    private int cleanupTickCounter = 0;
    // server is now inherited from BaseManager
    
    public PeekRequestManager() {
        // Cleanup is now handled by tick system - no need for separate scheduler
    }
    
    /**
     * Called every server tick to handle request timing
     */
    public void onServerTick() {
        tickTaskManager.processTick();
        
        // Cleanup every 30 seconds
        if (++cleanupTickCounter >= GameConstants.REQUEST_CLEANUP_INTERVAL_TICKS) {
            cleanupTickCounter = 0;
            cleanupExpiredRequests();
        }
    }
    
    /**
     * Sends a peek request from requester to target
     */
    public PeekConstants.Result<PeekRequest> sendRequest(ServerPlayerEntity requester, ServerPlayerEntity target) {
        try {
            // Validate request preconditions
            String validationError = RequestUtils.validateRequestPreconditions(
                requester, target, cooldownManager, playerRequestCounts
            );
            if (validationError != null) {
                return PeekConstants.Result.failure(validationError);
            }
            
            UUID requesterId = requester.getUuid();
            UUID targetId = target.getUuid();
            
            // Check if this is an invite response - if so, start session directly
            InviteManager inviteManager = ManagerRegistry.getInstance().getManager(InviteManager.class);
            if (inviteManager.hasActiveInvite(targetId, requesterId)) {
                // Validation already done above, consume the invite
                inviteManager.consumeInvite(targetId, requesterId);
                
                // Start peek session directly without request/confirmation
                PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
                PeekConstants.Result<PeekSession> sessionResult = sessionManager.startPeekSession(requester, target);
                if (sessionResult.isSuccess()) {
                    // Return success with a dummy request (since we bypassed the request system)
                    PeekRequest dummyRequest = new PeekRequest(requesterId, targetId, 
                        requester.getName().getString(), target.getName().getString(), 0L);
                    return PeekConstants.Result.success(dummyRequest);
                } else {
                    return PeekConstants.Result.failure(sessionResult.getError());
                }
            }
            
            // If requester is being peeked, stop those sessions first
            PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
            if (sessionManager.isPlayerBeingPeeked(requesterId)) {
                sessionManager.stopAllSessionsInvolving(requesterId, ServerPlayerCompat.getServer(requester));
                PeekMod.LOGGER.info("Cleared sessions involving player {} who wants to peek someone else",
                    ProfileCompat.getName(requester.getGameProfile()));
            }
            
            // If target has pending requests (waiting to confirm), don't allow new requests
            if (hasPendingRequestAsTarget(targetId)) {
                return PeekConstants.Result.failure(Text.translatable("peek.message.target_processing_request").getString());
            }
            
            // Get current requests for tracking
            int currentRequests = playerRequestCounts.getOrDefault(requesterId, 0);
            
            // Create request
            PeekRequest request = new PeekRequest(
                requesterId, targetId,
                ProfileCompat.getName(requester.getGameProfile()),
                ProfileCompat.getName(target.getGameProfile()),
                ModConfigManager.getRequestTimeoutSeconds()
            );
            
            // Store request
            activeRequests.put(request.getId(), request);
            playerRequestCounts.put(requesterId, currentRequests + 1);
            
            // Add to indexes for performance
            targetToRequestId.put(targetId, request.getId());
            requesterToRequestId.put(requesterId, request.getId());
            
            // Send notifications
            notificationHandler.sendRequestNotification(requester, target, request,
                    this::scheduleAutoAccept);
            
            // Schedule expiration using tick system
            int timeoutTicks = ModConfigManager.getRequestTimeoutSeconds() * PeekConstants.DEFAULT_STATIC_TICKS; // Convert seconds to ticks
            tickTaskManager.addTask(request.getId(), RequestConstants.TASK_TYPE_EXPIRE_REQUEST, timeoutTicks, 
                task -> expireRequest(task.getTaskId()));
            
            // Set cooldown
            cooldownManager.setCooldown(requesterId, ModConfigManager.getCooldownSeconds());
            
            LoggingHelper.logRequestOperation("Sent",
                ProfileCompat.getName(requester.getGameProfile()), ProfileCompat.getName(target.getGameProfile()));
            
            // Update command trees to show new options
            CommandUtils.updateCommandTreesForRequest(requester, target);
            
            return PeekConstants.Result.success(request);
            
        } catch (Exception e) {
            PeekMod.LOGGER.error("Error sending peek request", e);
            return PeekConstants.Result.failure(ErrorCodes.INTERNAL_ERROR);
        }
    }
    
    /**
     * Accepts a peek request
     */
    public PeekConstants.Result<PeekRequest> acceptRequest(ServerPlayerEntity player, UUID requestId) {
        PeekRequest request = activeRequests.get(requestId);
        if (request == null) {
            return PeekConstants.Result.failure(ErrorCodes.REQUEST_EXPIRED);
        }
        
        if (!request.getTargetId().equals(player.getUuid())) {
            return PeekConstants.Result.failure(ErrorCodes.INSUFFICIENT_PERMISSIONS);
        }
        
        if (!request.canAccept()) {
            return PeekConstants.Result.failure(ErrorCodes.REQUEST_EXPIRED);
        }
        
        // Re-validate session-related preconditions before accepting
        // Note: This is different from sendRequest validation - this checks current session states
        PeekConstants.Result<String> validationResult = validateSessionPreconditions(ServerPlayerCompat.getServer(player), request);
        if (!validationResult.isSuccess()) {
            removeRequest(requestId);
            return PeekConstants.Result.failure(validationResult.getError());
        }

        // Start peek session first before modifying request state
        MinecraftServer server = ServerPlayerCompat.getServer(player);
        if (server != null) {
            ServerPlayerEntity requester = server.getPlayerManager().getPlayer(request.getRequesterId());
            if (requester != null) {
                // Start peek session first
                PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
                PeekConstants.Result<PeekSession> sessionResult = sessionManager.startPeekSession(requester, player);
                if (sessionResult.isSuccess()) {
                    // Only modify request state after successful session start
                    request.setStatus(PeekRequest.RequestStatus.ACCEPTED);
                    removeRequest(requestId);
                    
                    // Send notifications after successful session start
                    notificationHandler.sendAcceptedNotifications(requester, player, request);
                    
                    // Update command trees after request processing
                    // startPeekSession updates for session-related commands, but we need to update for request-related commands
                    CommandUtils.updateCommandTreesForRequest(requester, player);
                    
                    return PeekConstants.Result.success(request);
                } else {
                    // Session failed to start - keep request in original state
                    // Return the actual error instead of generic internal error
                    return PeekConstants.Result.failure(sessionResult.getError());
                }
            }
        }
        
        return PeekConstants.Result.failure(ErrorCodes.PLAYER_OFFLINE);
    }
    
    /**
     * Denies a peek request
     */
    public PeekConstants.Result<PeekRequest> denyRequest(ServerPlayerEntity player, UUID requestId) {
        PeekRequest request = activeRequests.get(requestId);
        if (request == null) {
            return PeekConstants.Result.failure(ErrorCodes.REQUEST_EXPIRED);
        }
        
        if (!request.getTargetId().equals(player.getUuid())) {
            return PeekConstants.Result.failure(ErrorCodes.INSUFFICIENT_PERMISSIONS);
        }
        
        if (!request.canDeny()) {
            return PeekConstants.Result.failure(ErrorCodes.REQUEST_EXPIRED);
        }
        
        request.setStatus(PeekRequest.RequestStatus.DENIED);
        removeRequest(requestId);
        
        // Notify both players and get requester for command tree update
        MinecraftServer server = ServerPlayerCompat.getServer(player);
        ServerPlayerEntity requester = null;
        if (server != null) {
            requester = server.getPlayerManager().getPlayer(request.getRequesterId());
            if (requester != null) {
                notificationHandler.sendDeniedNotifications(requester, player, request);
            } else {
                // If requester is offline, still send notification to target
                notificationHandler.sendDeniedNotifications(null, player, request);
            }
        }
        
        // Update command trees for both players (requester might be null if offline)
        CommandUtils.updateCommandTreesForRequest(requester, player);
        
        return PeekConstants.Result.success(request);
    }
    
    /**
     * Cancels a request by the requester
     */
    public PeekConstants.Result<PeekRequest> cancelRequest(ServerPlayerEntity requester, UUID requestId) {
        PeekRequest request = activeRequests.get(requestId);
        if (request == null) {
            return PeekConstants.Result.failure(ErrorCodes.REQUEST_EXPIRED);
        }
        
        if (!request.getRequesterId().equals(requester.getUuid())) {
            return PeekConstants.Result.failure(ErrorCodes.INSUFFICIENT_PERMISSIONS);
        }
        
        if (!request.canCancel()) {
            return PeekConstants.Result.failure(ErrorCodes.REQUEST_EXPIRED);
        }
        
        request.setStatus(PeekRequest.RequestStatus.CANCELLED);
        removeRequest(requestId);
        
        // Notify both players
        MinecraftServer server = ServerPlayerCompat.getServer(requester);
        ServerPlayerEntity target = null;
        if (server != null) {
            target = server.getPlayerManager().getPlayer(request.getTargetId());
        }
        
        notificationHandler.sendCancelledNotifications(requester, target, request);
        
        // Update command trees for both players
        CommandUtils.updateCommandTreesForRequest(requester, target);
        
        return PeekConstants.Result.success(request);
    }
    
    /**
     * Gets pending requests for a player
     */
    public PeekRequest getPendingRequestForPlayer(UUID playerId) {
        UUID requestId = targetToRequestId.get(playerId);
        if (requestId != null) {
            PeekRequest request = activeRequests.get(requestId);
            if (request != null && request.getStatus() == PeekRequest.RequestStatus.PENDING) {
                return request;
            }
        }
        return null;
    }
    
    // sendRequestNotification method is now handled by NotificationHandler
    
    private void scheduleAutoAccept(UUID requestId, ServerPlayerEntity target, int delaySeconds) {
        int delayTicks = delaySeconds * PeekConstants.DEFAULT_STATIC_TICKS; // Convert seconds to ticks
        tickTaskManager.addTask(requestId, RequestConstants.TASK_TYPE_AUTO_ACCEPT, delayTicks, task -> {
            PeekRequest request = activeRequests.get(requestId);
            if (request != null && request.canAccept()) {
                MinecraftServer server = getCurrentServer();
                if (server != null) {
                    ServerPlayerEntity currentTarget = server.getPlayerManager().getPlayer(request.getTargetId());
                    if (currentTarget != null) {
                        PeekConstants.Result<PeekRequest> result = acceptRequest(currentTarget, requestId);
                        if (result.isSuccess()) {
                            currentTarget.sendMessage(Text.translatable("peek.message.auto_accept_completed")
                                .formatted(Formatting.GREEN), false);
                        }
                    }
                }
            }
            // Note: No need to track ScheduledFuture tasks as we use tick-based system
        });
        
        // Auto-accept task is now managed by tick system
    }
    
    private void cancelAutoAccept(UUID requestId) {
        tickTaskManager.removeTasksWithId(requestId);
    }
    
    
    private void expireRequest(UUID requestId) {
        PeekRequest request = activeRequests.get(requestId);
        if (request != null && request.getStatus() == PeekRequest.RequestStatus.PENDING) {
            request.setStatus(PeekRequest.RequestStatus.EXPIRED);
            removeRequest(requestId);
            
            // Update command trees for both players when request expires
            CommandUtils.updateCommandTreesForRequest(getCurrentServer(), request.getRequesterId(), request.getTargetId());
            
            // Notify both players about expiration
            // This would need server access - could be done through event system
            PeekMod.LOGGER.debug("Request {} expired", requestId);
        }
    }
    
    private void removeRequest(UUID requestId) {
        PeekRequest request = activeRequests.remove(requestId);
        if (request != null) {
            // Cancel auto-accept task if exists
            cancelAutoAccept(requestId);
            
            // Remove from indexes
            targetToRequestId.remove(request.getTargetId());
            requesterToRequestId.remove(request.getRequesterId());
            
            // Decrease request count for requester
            UUID requesterId = request.getRequesterId();
            int count = playerRequestCounts.getOrDefault(requesterId, 0);
            if (count > 1) {
                playerRequestCounts.put(requesterId, count - 1);
            } else {
                playerRequestCounts.remove(requesterId);
            }
        }
    }
    
    
    private void cleanupExpiredRequests() {
        // Collect expired request IDs first to avoid concurrent modification
        java.util.List<UUID> expiredRequestIds = activeRequests.entrySet().stream()
            .filter(entry -> entry.getValue().isExpired())
            .map(Map.Entry::getKey)
            .toList();
        
        // Remove expired requests using dedicated method
        for (UUID requestId : expiredRequestIds) {
            removeRequest(requestId);
        }
        
        // Also cleanup expired invites
        InviteManager inviteManager = ManagerRegistry.getInstance().getManager(InviteManager.class);
        inviteManager.cleanupExpiredInvites();
    }
    
    /**
     * Checks if a player has any active requests
     */
    public boolean hasActiveRequest(UUID playerId) {
        return activeRequests.values().stream()
            .anyMatch(request -> request.getRequesterId().equals(playerId) || request.getTargetId().equals(playerId));
    }
    
    /**
     * Checks if a player has pending requests as target (waiting to respond)
     */
    public boolean hasPendingRequestAsTarget(UUID playerId) {
        UUID requestId = targetToRequestId.get(playerId);
        if (requestId != null) {
            PeekRequest request = activeRequests.get(requestId);
            return request != null && request.getStatus() == PeekRequest.RequestStatus.PENDING;
        }
        return false;
    }
    
    /**
     * Checks if a player has pending requests as requester (waiting for response)
     */
    public boolean hasPendingRequestAsRequester(UUID playerId) {
        UUID requestId = requesterToRequestId.get(playerId);
        if (requestId != null) {
            PeekRequest request = activeRequests.get(requestId);
            return request != null && request.getStatus() == PeekRequest.RequestStatus.PENDING;
        }
        return false;
    }
    
    /**
     * Gets the pending request where the player is the requester
     */
    public PeekRequest getPendingRequestAsRequester(UUID playerId) {
        UUID requestId = requesterToRequestId.get(playerId);
        if (requestId != null) {
            PeekRequest request = activeRequests.get(requestId);
            if (request != null && request.getStatus() == PeekRequest.RequestStatus.PENDING) {
                return request;
            }
        }
        return null;
    }
    
    /**
     * Checks if there's a pending request between two players (in either direction)
     */
    public boolean hasPendingRequestBetween(UUID player1Id, UUID player2Id) {
        return activeRequests.values().stream()
            .anyMatch(request -> request.getStatus() == PeekRequest.RequestStatus.PENDING && 
                     ((request.getRequesterId().equals(player1Id) && request.getTargetId().equals(player2Id)) ||
                      (request.getRequesterId().equals(player2Id) && request.getTargetId().equals(player1Id))));
    }
    
    /**
     * Gets the pending request between two players (in either direction) 
     */
    public PeekRequest getPendingRequestBetween(UUID player1Id, UUID player2Id) {
        return activeRequests.values().stream()
            .filter(request -> request.getStatus() == PeekRequest.RequestStatus.PENDING && 
                     ((request.getRequesterId().equals(player1Id) && request.getTargetId().equals(player2Id)) ||
                      (request.getRequesterId().equals(player2Id) && request.getTargetId().equals(player1Id))))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Cancel all pending requests between two players (used for blacklist)
     * @param player1Id First player UUID
     * @param player2Id Second player UUID
     */
    public void cancelRequestsBetween(UUID player1Id, UUID player2Id) {
        activeRequests.entrySet().removeIf(entry -> {
            PeekRequest request = entry.getValue();
            boolean shouldCancel = (request.getRequesterId().equals(player1Id) && request.getTargetId().equals(player2Id)) ||
                                   (request.getRequesterId().equals(player2Id) && request.getTargetId().equals(player1Id));
            if (shouldCancel && request.getStatus() == PeekRequest.RequestStatus.PENDING) {
                // Clean up associated data
                removeRequest(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Validates session-related preconditions for request acceptance
     */
    private PeekConstants.Result<String> validateSessionPreconditions(MinecraftServer server, PeekRequest request) {
        if (server == null) {
            return PeekConstants.Result.failure(ErrorCodes.INTERNAL_ERROR);
        }
        
        ServerPlayerEntity requester = server.getPlayerManager().getPlayer(request.getRequesterId());
        if (requester == null) {
            return PeekConstants.Result.failure(ErrorCodes.PLAYER_OFFLINE);
        }
        
        UUID requesterId = requester.getUuid();
        UUID targetId = request.getTargetId();
        
        // Check if already peeking (could have started another peek after sending request)
        PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
        if (sessionManager.isPlayerPeeking(requesterId)) {
            // Check if already peeking the same target
            UUID currentTargetId = sessionManager.getCurrentTarget(requesterId);
            if (currentTargetId != null && currentTargetId.equals(targetId)) {
                return PeekConstants.Result.failure(ErrorCodes.ALREADY_PEEKING_TARGET);
            }
            // Allow peek switching - don't fail for already peeking different target
        }
        
        // Check if target is being peeked by someone else (could have changed)
        if (sessionManager.isPlayerBeingPeeked(targetId)) {
            return PeekConstants.Result.failure(ErrorCodes.BEING_PEEKED);
        }
        
        return PeekConstants.Result.success("Validation passed");
    }

    @Override
    public void shutdown() {
        // Clear tick tasks
        tickTaskManager.clear();
        
        // Clear all data
        activeRequests.clear();
        playerRequestCounts.clear();
        targetToRequestId.clear();
        requesterToRequestId.clear();
        
        super.shutdown();
    }
}