package com.peek.utils;

import com.peek.PeekMod;
import com.peek.config.ModConfigManager;
import com.peek.data.PeekDataStorage;
import com.peek.data.peek.PlayerPeekData;
import com.peek.manager.ManagerRegistry;
import com.peek.manager.PeekRequestManager;
import com.peek.manager.PeekSessionManager;
import com.peek.manager.constants.ErrorCodes;
import com.peek.utils.compat.ProfileCompat;
import com.peek.utils.compat.ServerPlayerCompat;
import com.peek.utils.permissions.Permissions;
import eu.pb4.playerdata.api.PlayerDataApi;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Utility class for request management operations
 * Extracted from PeekRequestManager to reduce method complexity
 */
public class RequestUtils {
    
    /**
     * Player states for validation logic
     */
    private enum RequesterState {
        NORMAL,              // 正常状态
        PEEKING_DIFFERENT,   // 正在 peek 其他人（允许切换）
        PEEKING_SAME,        // 正在 peek 相同目标（不允许）
        HAS_PENDING_REQUEST  // 有待处理的请求
    }
    
    private enum TargetState {
        AVAILABLE,           // 可以被 peek
        BEING_PEEKED,       // 正在被 peek（不允许）
        SESSION_LIMIT_FULL,  // 会话数量已满
        PRIVATE_MODE,        // 私密模式
        BLACKLISTED         // 已拉黑
    }

    /**
     * Validates all preconditions for sending a peek request using state matrix logic
     * @param requester The player sending the request
     * @param target The target player
     * @param cooldownManager The cooldown manager instance
     * @param playerRequestCounts Map of player request counts
     * @return null if validation passes, error message if validation fails
     */
    public static String validateRequestPreconditions(
            ServerPlayerEntity requester, ServerPlayerEntity target, 
            CooldownManager cooldownManager,
            java.util.Map<UUID, Integer> playerRequestCounts) {
        
        UUID requesterId = requester.getUuid();
        UUID targetId = target.getUuid();
        
        // 1. Universal validations (apply regardless of state)
        String universalError = validateUniversalRules(requester, target, cooldownManager, playerRequestCounts);
        if (universalError != null) return universalError;
        
        // 2. State-based validation using matrix logic
        RequesterState requesterState = getRequesterState(requesterId, targetId);
        TargetState targetState = getTargetState(requester, target, targetId);
        
        // 3. Check state compatibility matrix
        return validateStateMatrix(requesterState, targetState);
    }
    
    /**
     * Universal rules that apply regardless of player state
     */
    private static String validateUniversalRules(ServerPlayerEntity requester, ServerPlayerEntity target,
                                               CooldownManager cooldownManager,
                                               java.util.Map<UUID, Integer> playerRequestCounts) {
        // Basic validation
        if (requester.equals(target)) {
            return ErrorCodes.CANNOT_PEEK_SELF.getTranslationKey();
        }
        
        // Safety validations
        if (!ValidationUtils.validateNoHostileMobsAround(requester)) {
            return ErrorCodes.HOSTILE_MOBS_NEARBY.getTranslationKey();
        }
        
        if (!ValidationUtils.validateMinimumDistance(requester, target)) {
            return ErrorCodes.TOO_CLOSE_SAME_DIMENSION.getTranslationKey();
        }
        
        if (!isPlayerStationary(requester) && !ValidationUtils.canBypass(requester, Permissions.Bypass.MOVEMENT, 2)) {
            return ErrorCodes.PLAYER_NOT_STATIONARY.getTranslationKey();
        }
        
        // Cooldown check
        if (cooldownManager.isOnCooldown(requester)) {
            long remaining = cooldownManager.getRemainingCooldown(requester.getUuid());
            return ErrorCodes.COOLDOWN_ACTIVE.getTranslationKey() + ":" + remaining;
        }
        
        // Request limit check
        int currentRequests = playerRequestCounts.getOrDefault(requester.getUuid(), 0);
        if (currentRequests >= ModConfigManager.getMaxConcurrentRequestsPerPlayer() &&
            !ValidationUtils.canBypass(requester, Permissions.Bypass.MAX_SESSIONS, 2)) {
            return ErrorCodes.REQUEST_LIMIT_EXCEEDED.getTranslationKey();
        }
        
        // Configuration restrictions
        double maxDistance = ModConfigManager.getMaxDistance();
        if (maxDistance > 0 && !ValidationUtils.canBypass(requester, Permissions.Bypass.DISTANCE, 2)) {
            double distance = ServerPlayerCompat.getPos(requester).distanceTo(ServerPlayerCompat.getPos(target));
            if (distance > maxDistance) {
                return ErrorCodes.DISTANCE_EXCEEDED.getTranslationKey();
            }
        }

        if (!ModConfigManager.isAllowCrossDimension() && !ValidationUtils.canBypass(requester, Permissions.Bypass.DIMENSION, 2)) {
            if (!ServerPlayerCompat.getWorld(requester).getRegistryKey().equals(ServerPlayerCompat.getWorld(target).getRegistryKey())) {
                return ErrorCodes.DIMENSION_NOT_ALLOWED.getTranslationKey();
            }
        }
        
        return null;
    }
    
    /**
     * Determines the current state of the requester
     */
    private static RequesterState getRequesterState(UUID requesterId, UUID targetId) {
        if (ManagerRegistry.getInstance().getManager(PeekSessionManager.class).isPlayerPeeking(requesterId)) {
            UUID currentTargetId = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).getCurrentTarget(requesterId);
            if (currentTargetId != null && currentTargetId.equals(targetId)) {
                return RequesterState.PEEKING_SAME;
            }
            return RequesterState.PEEKING_DIFFERENT;
        }
        
        if (ManagerRegistry.getInstance().getManager(PeekRequestManager.class).hasPendingRequestAsRequester(requesterId)) {
            return RequesterState.HAS_PENDING_REQUEST;
        }
        
        return RequesterState.NORMAL;
    }
    
    /**
     * Determines the current state of the target
     */
    private static TargetState getTargetState(ServerPlayerEntity requester, ServerPlayerEntity target, UUID targetId) {
        // Check if being peeked
        if (ManagerRegistry.getInstance().getManager(PeekSessionManager.class).isPlayerBeingPeeked(targetId)) {
            return TargetState.BEING_PEEKED;
        }
        
        // Check session limit
        int maxSessionsPerPlayer = ModConfigManager.getMaxPeekSessionsPerPlayer();
        if (maxSessionsPerPlayer > 0 && !ValidationUtils.canBypass(requester, Permissions.Bypass.MAX_SESSIONS, 2)) {
            int targetCurrentSessions = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).getSessionsTargeting(targetId).size();
            if (targetCurrentSessions >= maxSessionsPerPlayer) {
                return TargetState.SESSION_LIMIT_FULL;
            }
        }
        
        // Check target permissions
        PlayerPeekData targetData = PlayerDataApi.getCustomDataFor(target, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
        if (targetData == null) {
            targetData = PlayerPeekData.createDefault();
        }
        
        // Check private mode first
        if (targetData.privateMode() && !ValidationUtils.canBypass(requester, Permissions.Bypass.PRIVATE_MODE, 2)) {
            return TargetState.PRIVATE_MODE;
        }
        
        // Check if requester is blacklisted by target
        if (targetData.blacklist().containsKey(requester.getUuid()) && !ValidationUtils.canBypass(requester, Permissions.Bypass.BLACKLIST, 2)) {
            return TargetState.BLACKLISTED;
        }
        
        return TargetState.AVAILABLE;
    }
    
    /**
     * State compatibility matrix - determines if a request can be made based on requester and target states
     */
    private static String validateStateMatrix(RequesterState requesterState, TargetState targetState) {
        // First check target state - if target is unavailable, request fails regardless of requester state
        switch (targetState) {
            case BEING_PEEKED:
                return ErrorCodes.BEING_PEEKED.getTranslationKey();
            case SESSION_LIMIT_FULL:
                return ErrorCodes.SESSION_LIMIT_EXCEEDED.getTranslationKey();
            case PRIVATE_MODE:
                return ErrorCodes.PRIVATE_MODE.getTranslationKey();
            case BLACKLISTED:
                return ErrorCodes.BLACKLISTED.getTranslationKey();
            case AVAILABLE:
                break; // Continue to check requester state
        }
        
        // Then check requester state compatibility
        return switch (requesterState) {
            case NORMAL, PEEKING_DIFFERENT -> {
                // These states can send requests
                yield null;
            }
            case PEEKING_SAME -> ErrorCodes.ALREADY_PEEKING_TARGET.getTranslationKey();
            case HAS_PENDING_REQUEST -> ErrorCodes.REQUEST_LIMIT_EXCEEDED.getTranslationKey();
            default -> ErrorCodes.INTERNAL_ERROR.getTranslationKey();
        };
    }

    /*
      Validates request preconditions with exception handling
      @param requester The player sending the request
     * @param target The target player  
     * @param cooldownManager The cooldown manager instance
     * @param playerRequestCounts Map of player request counts
     * @throws ValidationException if validation fails
     */
    // Removed unused validateRequestPreconditionsWithExceptions method

    /**
     * Checks if a player is stationary (not moving significantly)
     */
    private static boolean isPlayerStationary(ServerPlayerEntity player) {
        // Allow small movements (e.g., looking around) - same logic as in PeekSessionManager
        return player.getVelocity().lengthSquared() < 0.01; // Very small threshold for "stationary"
    }
}