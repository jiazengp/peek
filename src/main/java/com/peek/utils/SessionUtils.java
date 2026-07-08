package com.peek.utils;

import com.peek.PeekMod;
import com.peek.data.peek.PeekSession;
import com.peek.utils.SoundManager;
import com.peek.data.peek.PlayerState;
import com.peek.manager.exceptions.PeekException;
import com.peek.manager.exceptions.SessionException;
import com.peek.manager.constants.ErrorCodes;
import com.peek.utils.compat.ProfileCompat;
import com.peek.utils.compat.ServerPlayerCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for session management operations
 * Extracted from PeekSessionManager to reduce method complexity
 */
public class SessionUtils {
    
    /**
     * Handles cleanup of sessions targeting a player who wants to become a peeker
     * @param peekerId The player who wants to start peeking
     * @param targetId The target they want to peek
     * @param peeker The peeker player entity
     * @param targetToSession Map of target IDs to session IDs
     * @param activeSessions Map of active sessions
     * @param recentCircularPeeks Map tracking recent circular peeks
     * @param stopSessionCallback Callback to stop a session
     * @return null if successful, error message if failed
     */
    public static String handleTargetingSessionCleanup(
            UUID peekerId, UUID targetId, ServerPlayer peeker,
            Map<UUID, Set<UUID>> targetToSession,
            Map<UUID, PeekSession> activeSessions,
            Map<String, Long> recentCircularPeeks,
            SessionStopCallback stopSessionCallback) {
        
        Set<UUID> peekerTargetingSessions = targetToSession.get(peekerId);
        if (peekerTargetingSessions == null || peekerTargetingSessions.isEmpty()) {
            return null; // No cleanup needed
        }
        
        PeekMod.LOGGER.info("Player {} is being peeked by others, cleaning up {} sessions before starting new peek",
            ProfileCompat.getName(peeker.getGameProfile()), peekerTargetingSessions.size());
        
        // Copy to avoid concurrent modification
        Set<UUID> sessionsToStop = new HashSet<>(peekerTargetingSessions);
        for (UUID sessionId : sessionsToStop) {
            PeekSession session = activeSessions.get(sessionId);
            if (session != null) {
                UUID otherPeekerId = session.getPeekerId();
                boolean isCircularPeek = targetId.equals(otherPeekerId);
                
                // Check for ping-pong prevention
                if (isCircularPeek) {
                    String pairKey = createPairKey(peekerId, targetId);
                    Long lastSwapTime = recentCircularPeeks.get(pairKey);
                    long currentTime = System.currentTimeMillis();
                    
                    // Prevent rapid ping-pong (within 10 seconds)
                    if (lastSwapTime != null && (currentTime - lastSwapTime) < 10000) {
                        PeekMod.LOGGER.info("Preventing ping-pong peek between {} and {} (too recent)",
                            ProfileCompat.getName(peeker.getGameProfile()), session.getPeekerName());
                        return "Circular peek too frequent, please wait a moment";
                    }
                    
                    // Record this circular peek
                    recentCircularPeeks.put(pairKey, currentTime);
                }
                
                PeekMod.LOGGER.info("Stopping session where {} was peeking {} (circular: {})",
                    session.getPeekerName(), ProfileCompat.getName(peeker.getGameProfile()), isCircularPeek);

                // Send better notification to interrupted player
                ServerPlayer interruptedPlayer = ServerPlayerCompat.getServer(peeker).getPlayerList().getPlayer(otherPeekerId);
                if (interruptedPlayer != null) {
                    String messageKey = isCircularPeek ?
                        "peek.message.interrupted_by_circular_peek" :
                        "peek.message.interrupted_by_target_peek";
                    Component message = Component.translatable(messageKey, ProfileCompat.getName(peeker.getGameProfile()));
                    interruptedPlayer.sendSystemMessage(message, false);

                    PeekMod.LOGGER.info("Notified {} about their peek session being interrupted by {}",
                        session.getPeekerName(), ProfileCompat.getName(peeker.getGameProfile()));
                }

                // Use callback to stop the session
                stopSessionCallback.stopSession(otherPeekerId, false, ServerPlayerCompat.getServer(peeker));
            }
        }
        
        return null; // Success
    }
    
    /**
     * Handles the rollback process when session creation fails
     */
    public static void handleSessionRollback(
            UUID peekerId, UUID targetId, UUID sessionId,
            boolean wasSwitching, PeekSession currentSessionToRestore,
            Map<UUID, PeekSession> activeSessions,
            Map<UUID, UUID> peekerToSession,
            Map<UUID, Set<UUID>> targetToSession,
            ServerPlayer peeker,
            RollbackHandler rollbackHandler) {
        
        PeekMod.LOGGER.error("Failed to teleport peeker, rolling back session");
        
        // Remove session mappings
        activeSessions.remove(sessionId);
        peekerToSession.remove(peekerId);
        Set<UUID> targetSessions = targetToSession.get(targetId);
        if (targetSessions != null) {
            targetSessions.remove(sessionId);
            if (targetSessions.isEmpty()) {
                targetToSession.remove(targetId);
            }
        }
        
        // Handle rollback for peek switching vs new session
        if (wasSwitching && currentSessionToRestore != null) {
            rollbackHandler.handleSwitchingRollback(peeker, currentSessionToRestore, peekerId);
        } else {
            rollbackHandler.handleNewSessionRollback(peeker);
        }
    }
    
    /**
     * Sends appropriate notification messages for session start
     */
    public static void sendSessionStartNotifications(
            ServerPlayer peeker, ServerPlayer target,
            boolean wasSwitching) {
        
        // Send notifications - check if this was a switch
        String messageKey = wasSwitching ? "peek.peek_switched" : "peek.peek_started";
        Component peekerMessage = Component.translatable(messageKey, ProfileCompat.getName(target.getGameProfile()))
            .withStyle(ChatFormatting.GREEN);
        peeker.sendSystemMessage(peekerMessage, false);

        if (wasSwitching) {
            PeekMod.LOGGER.info("Player {} successfully switched peek from previous target to {}",
                ProfileCompat.getName(peeker.getGameProfile()), ProfileCompat.getName(target.getGameProfile()));
        }

        Component targetMessage = Component.translatable("peek.being_peeked", ProfileCompat.getName(peeker.getGameProfile()))
            .withStyle(ChatFormatting.YELLOW);
        target.sendSystemMessage(targetMessage, false);
        
        // Play sound to target indicating they are being peeked
        SoundManager.playBeingPeekedSound(target);
    }
    
    /**
     * Creates a unique key for a pair of players (order-independent)
     */
    private static String createPairKey(UUID player1, UUID player2) {
        // Create deterministic key regardless of order
        String p1Str = player1.toString();
        String p2Str = player2.toString();
        if (p1Str.compareTo(p2Str) < 0) {
            return p1Str + "-" + p2Str;
        } else {
            return p2Str + "-" + p1Str;
        }
    }
    
    /**
     * Callback interface for stopping sessions
     */
    @FunctionalInterface
    public interface SessionStopCallback {
        void stopSession(UUID peekerId, boolean voluntary, net.minecraft.server.MinecraftServer server);
    }
    
    /**
     * Handles player state restoration for session end
     */
    public static void handlePlayerStateRestoration(
            ServerPlayer peeker, PeekSession session, 
            PlayerPeekDataHandler dataHandler) {
        
        PlayerState originalState = session.getOriginalPeekerState();
        var registryManager = com.peek.utils.compat.PlayerCompat.getRegistryManager(peeker);
        if (registryManager == null) {
            throw new IllegalStateException("Cannot restore player state - server or registry manager not available");
        }
        originalState.restore(peeker, registryManager);
        
        // Clear saved state only after successful restoration
        dataHandler.clearSavedState(peeker);

        PeekMod.LOGGER.info("Restored state for player {} after session end", ProfileCompat.getName(peeker.getGameProfile()));
    }
    
    /**
     * Handles offline player state preservation
     */
    public static void handleOfflinePlayerState(
            net.minecraft.server.MinecraftServer server, PeekSession session,
            PlayerPeekDataHandler dataHandler) {
        
        PlayerState originalState = session.getOriginalPeekerState();
        if (originalState != null) {
            try {
                dataHandler.saveOfflineState(server, session.getPeekerId(), originalState);
                PeekMod.LOGGER.info("Player {} is offline, saved original state to PlayerDataAPI for crash recovery", 
                    session.getPeekerName());
            } catch (Exception e) {
                PeekMod.LOGGER.error("Failed to save state for offline player {} using PlayerDataAPI", 
                    session.getPeekerName(), e);
            }
        } else {
            PeekMod.LOGGER.warn("No original state found for offline player {}", session.getPeekerName());
        }
    }
    
    /**
     * Sends session end notifications to involved players
     */
    public static void sendSessionEndNotifications(
            net.minecraft.server.MinecraftServer server, PeekSession session, 
            boolean voluntary) {
        
        // Send notification to peeker
        ServerPlayer peeker = server.getPlayerList().getPlayer(session.getPeekerId());
        if (peeker != null) {
            String reason = voluntary ? "manually stopped" : "automatically stopped";
            net.minecraft.network.chat.Component endMessage = net.minecraft.network.chat.Component.translatable("peek.message.ended_normal")
                .withStyle(net.minecraft.ChatFormatting.YELLOW);
            peeker.sendSystemMessage(endMessage, false);
        }
        
        // Send notification to target (the player who was being peeked)
        ServerPlayer target = server.getPlayerList().getPlayer(session.getTargetId());
        if (target != null) {
            String reasonKey = voluntary ? "manually" : "automatically";
            net.minecraft.network.chat.Component targetMessage = net.minecraft.network.chat.Component.translatable("peek.message.ended_by_target", session.getPeekerName())
                .withStyle(net.minecraft.ChatFormatting.GRAY);
            target.sendSystemMessage(targetMessage, false);
        }
    }
    
    /**
     * Interface for handling rollback operations
     */
    public interface RollbackHandler {
        void handleSwitchingRollback(ServerPlayer peeker, PeekSession sessionToRestore, UUID peekerId);
        void handleNewSessionRollback(ServerPlayer peeker);
    }
    
    /**
     * Interface for handling PlayerData operations
     */
    public interface PlayerPeekDataHandler {
        void clearSavedState(ServerPlayer player);
        void saveOfflineState(net.minecraft.server.MinecraftServer server, UUID playerId, PlayerState state);
    }
    
    // Removed unused safeTeleportPeekerToTarget method
    
    /**
     * Safely handles session operations with proper exception handling
     */
    public static void safeSessionOperation(
            UUID sessionId, SessionException.SessionOperation operation,
            Runnable operationCode) throws SessionException {
        try {
            operationCode.run();
        } catch (Exception e) {
            throw new SessionException(
                operation,
                ErrorCodes.OPERATION_FAILED.name(),
                "Session operation failed: " + operation.name(),
                e, sessionId, null, null
            );
        }
    }
}

