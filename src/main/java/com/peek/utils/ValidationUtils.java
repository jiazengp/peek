package com.peek.utils;

import com.peek.PeekMod;
import com.peek.config.ModConfigManager;
import com.peek.data.PeekDataStorage;
import com.peek.data.peek.PeekRequest;
import com.peek.data.peek.PlayerPeekData;
import com.peek.manager.ManagerRegistry;
import com.peek.manager.PeekSessionManager;
import com.peek.manager.PeekRequestManager;
import com.peek.utils.compat.ServerPlayerCompat;
import com.peek.utils.permissions.PermissionChecker;
import eu.pb4.playerdata.api.PlayerDataApi;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.GameType;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Utility class for common validation patterns in commands
 */
public class ValidationUtils {
    
    /**
     * Validates that a command source has the required permission
     * @param source Command source to check
     * @param permission Permission node to check
     * @param fallbackLevel Fallback permission level (0-4)
     * @return true if has permission, false otherwise
     */
    public static boolean validatePermission(CommandSourceStack source, String permission, int fallbackLevel) {
        if (!PermissionChecker.hasPermission(source, permission, fallbackLevel)) {
            source.sendFailure(Component.translatable("peek.error.no_permission").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }
    
    /**
     * Validates that a player has the required permission
     * @param player Player to check
     * @param permission Permission node to check
     * @param fallbackLevel Fallback permission level (0-4)
     * @return true if has permission, false otherwise
     */
    public static boolean validatePermission(ServerPlayer player, String permission, int fallbackLevel) {
        if (!PermissionChecker.hasPermission(player, permission, fallbackLevel)) {
            player.sendSystemMessage(Component.translatable("peek.error.no_permission").withStyle(ChatFormatting.RED), false);
            return false;
        }
        return true;
    }
    
    /**
     * Checks if player can bypass a specific restriction
     * @param player Player to check
     * @param bypassPermission Bypass permission node
     * @param opLevel OP level required to bypass (typically 2-4)
     * @return true if player can bypass, false otherwise
     */
    public static boolean canBypass(ServerPlayer player, String bypassPermission, int opLevel) {
        return PermissionChecker.hasPermission(player, bypassPermission, opLevel);
    }
    
    /**
     * Checks if command source can bypass a specific restriction  
     * @param source Command source to check
     * @param bypassPermission Bypass permission node
     * @param opLevel OP level required to bypass (typically 2-4)
     * @return true if source can bypass, false otherwise
     */
    public static boolean canBypass(CommandSourceStack source, String bypassPermission, int opLevel) {
        return PermissionChecker.hasPermission(source, bypassPermission, opLevel);
    }
    
    /**
     * Validates that a player is not null
     * @param player Player to validate
     * @param executor Player who will receive error message
     * @return true if valid, false if null
     */
    public static boolean validatePlayerNotNull(ServerPlayer player, ServerPlayer executor) {
        if (player == null) {
            executor.sendSystemMessage(Component.translatable("peek.error.player_not_found").withStyle(ChatFormatting.RED), false);
            return false;
        }
        return true;
    }
    
    /**
     * Validates that a pending request exists
     * @param playerId Player UUID
     * @param executor Player who will receive error message
     * @return the request if valid, null if invalid
     */
    public static PeekRequest validatePendingRequest(java.util.UUID playerId, ServerPlayer executor) {
        PeekRequest request = ManagerRegistry.getInstance().getManager(PeekRequestManager.class).getPendingRequestForPlayer(playerId);
        if (request == null) {
            executor.sendSystemMessage(Component.translatable("peek.error.request_expired").withStyle(ChatFormatting.RED), false);
            return null;
        }
        return request;
    }
    
    /**
     * Validates that player has pending requests as requester
     * @param playerId Player UUID
     * @param executor Player who will receive error message
     * @return true if valid, false if invalid
     */
    public static boolean validateHasPendingRequest(java.util.UUID playerId, ServerPlayer executor) {
        if (!ManagerRegistry.getInstance().getManager(PeekRequestManager.class).hasPendingRequestAsRequester(playerId)) {
            executor.sendSystemMessage(Component.translatable("peek.error.no_pending_request").withStyle(ChatFormatting.RED), false);
            return false;
        }
        return true;
    }
    
    /**
     * Validates that player is currently peeking someone
     * @param playerId Player UUID
     * @param executor Player who will receive error message
     * @return true if valid, false if invalid
     */
    public static boolean validatePlayerPeeking(java.util.UUID playerId, ServerPlayer executor) {
        if (!ManagerRegistry.getInstance().getManager(PeekSessionManager.class).isPlayerPeeking(playerId)) {
            executor.sendSystemMessage(Component.translatable("peek.manage.player_not_peeking").withStyle(ChatFormatting.RED), false);
            return false;
        }
        return true;
    }
    
    /**
     * Validates blacklist operations - checks if player is already in blacklist
     * @param data Player peek data
     * @param targetId Target player UUID
     * @param targetName Target player display name
     * @param executor Player who will receive error message
     * @param isAddOperation true for add operation, false for remove operation
     * @return true if valid, false if invalid
     */
    public static boolean validateBlacklistOperation(PlayerPeekData data, java.util.UUID targetId, 
                                                   Component targetName, ServerPlayer executor, boolean isAddOperation) {
        boolean inBlacklist = data.blacklist().containsKey(targetId);
        
        if (isAddOperation && inBlacklist) {
            executor.sendSystemMessage(Component.translatable("peek.blacklist.already_exists", targetName)
                .withStyle(ChatFormatting.YELLOW), false);
            return false;
        } else if (!isAddOperation && !inBlacklist) {
            executor.sendSystemMessage(Component.translatable("peek.blacklist.not_exists", targetName)
                .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        
        return true;
    }
    
    /**
     * Validates that a collection is not empty
     * @param collection Collection to validate
     * @param emptyMessageSupplier Supplier for empty message
     * @param executor Player who will receive message
     * @param <T> Collection type
     * @return true if not empty, false if empty
     */
    public static <T> boolean validateCollectionNotEmpty(Collection<T> collection, 
                                                        Supplier<Component> emptyMessageSupplier,
                                                        ServerPlayer executor) {
        if (collection.isEmpty()) {
            executor.sendSystemMessage(emptyMessageSupplier.get(), false);
            return false;
        }
        return true;
    }
    
    /**
     * Validates that a collection is not empty for admin commands (sends error to command source)
     * @param collection Collection to validate
     * @param errorMessage Error message to send
     * @param source Command source
     * @param <T> Collection type
     * @return true if not empty, false if empty
     */
    public static <T> boolean validateCollectionNotEmptyAdmin(Collection<T> collection, 
                                                            Component errorMessage,
                                                            net.minecraft.commands.CommandSourceStack source) {
        if (collection.isEmpty()) {
            source.sendFailure(errorMessage);
            return false;
        }
        return true;
    }
    
    /**
     * Validates that a Result is successful and sends error message if not
     * @param result Result to validate
     * @param executor Player who will receive error message
     * @param <T> Result type
     * @return true if successful, false if not
     */
    public static <T> boolean validateResult(com.peek.manager.constants.PeekConstants.Result<T> result, ServerPlayer executor) {
        if (!result.isSuccess()) {
            executor.sendSystemMessage(CommandUtils.getErrorMessage(result.getError()), false);
            return false;
        }
        return true;
    }
    
    /**
     * Validates peek session relationship (peeker is actually peeking the target)
     * @param session Peek session
     * @param targetId Expected target ID  
     * @param peekerName Peeker display name
     * @param executor Player who will receive error message
     * @return true if valid, false if invalid
     */
    public static boolean validateSessionRelationship(com.peek.data.peek.PeekSession session, 
                                                    java.util.UUID targetId, Component peekerName, 
                                                    ServerPlayer executor) {
        if (session == null || !session.getTargetId().equals(targetId)) {
            executor.sendSystemMessage(Component.translatable("peek.message.player_not_peeking", peekerName)
                .withStyle(ChatFormatting.RED), false);
            return false;
        }
        return true;
    }
    
    // Command requires validation helpers
    
    /**
     * Check if command source meets basic requirements (permission + player only)
     * @param source command source
     * @param permission required permission
     * @param opLevel minimum OP level
     * @return true if requirements met
     */
    public static boolean requiresPlayerWithPermission(CommandSourceStack source, String permission, int opLevel) {
        return PermissionChecker.hasPermission(source, permission, opLevel) && source.isPlayer();
    }
    
    /**
     * Check if player is currently peeking someone (for stop command visibility)
     * @param source command source
     * @param permission required permission  
     * @param opLevel minimum OP level
     * @return true if player is peeking and has permission
     */
    public static boolean requiresActivePeeker(CommandSourceStack source, String permission, int opLevel) {
        if (!requiresPlayerWithPermission(source, permission, opLevel)) {
            return false;
        }
        try {
            return ManagerRegistry.getInstance().getManager(PeekSessionManager.class).isPlayerPeeking(source.getPlayerOrException().getUUID());
        } catch (Exception e) {
            PeekMod.LOGGER.error(e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if player is being peeked (for who/cancel-player command visibility)
     * @param source command source
     * @return true if player is being peeked
     */
    public static boolean requiresBeingPeeked(CommandSourceStack source) {
        if (!source.isPlayer()) {
            return false;
        }
        try {
            return !ManagerRegistry.getInstance().getManager(PeekSessionManager.class).getSessionsTargeting(source.getPlayerOrException().getUUID()).isEmpty();
        } catch (Exception e) {
            PeekMod.LOGGER.error(e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if player has pending request (for accept/deny command visibility)
     * @param source command source
     * @param permission required permission
     * @param opLevel minimum OP level
     * @return true if player has pending request and permission
     */
    public static boolean requiresPendingRequest(CommandSourceStack source, String permission, int opLevel) {
        if (!requiresPlayerWithPermission(source, permission, opLevel)) {
            return false;
        }
        try {
            return ManagerRegistry.getInstance().getManager(PeekRequestManager.class).getPendingRequestForPlayer(source.getPlayerOrException().getUUID()) != null;
        } catch (Exception e) {
            PeekMod.LOGGER.error(e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if player has pending request as requester (for cancel command visibility)
     * @param source command source
     * @return true if player has pending request as requester
     */
    public static boolean requiresPendingRequestAsRequester(CommandSourceStack source) {
        if (!source.isPlayer()) {
            return false;
        }
        try {
            return ManagerRegistry.getInstance().getManager(PeekRequestManager.class).hasPendingRequestAsRequester(source.getPlayerOrException().getUUID());
        } catch (Exception e) {
            PeekMod.LOGGER.error(e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if player has any pending request (for accept/deny command visibility - no permission check)
     * @param source command source
     * @return true if player has pending request
     */
    public static boolean hasPendingRequest(CommandSourceStack source) {
        if (!source.isPlayer()) {
            return false;
        }
        try {
            return ManagerRegistry.getInstance().getManager(PeekRequestManager.class).getPendingRequestForPlayer(source.getPlayerOrException().getUUID()) != null;
        } catch (Exception e) {
            PeekMod.LOGGER.error(e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if player is currently peeking someone (for stop command visibility - no permission check)
     * @param source command source
     * @return true if player is peeking
     */
    public static boolean isPlayerPeeking(CommandSourceStack source) {
        if (!source.isPlayer()) {
            return false;
        }
        try {
            return ManagerRegistry.getInstance().getManager(PeekSessionManager.class).isPlayerPeeking(source.getPlayerOrException().getUUID());
        } catch (Exception e) {
            PeekMod.LOGGER.error(e.getMessage());
            return false;
        }
    }
    
    /**
     * Validate accept command permission and requirements
     * @param player Player to validate
     * @return true if valid, false otherwise
     */
    public static boolean validateAcceptPermission(ServerPlayer player) {
        return validatePermission(player, com.peek.utils.permissions.Permissions.Command.ACCEPT, 0);
    }
    
    /**
     * Validate deny command permission and requirements
     * @param player Player to validate
     * @return true if valid, false otherwise
     */
    public static boolean validateDenyPermission(ServerPlayer player) {
        return validatePermission(player, com.peek.utils.permissions.Permissions.Command.DENY, 0);
    }
    
    /**
     * Validate stop command permission and requirements
     * @param player Player to validate
     * @return true if valid, false otherwise
     */
    public static boolean validateStopPermission(ServerPlayer player) {
        return validatePermission(player, com.peek.utils.permissions.Permissions.Command.STOP, 0);
    }
    
    /**
     * Check if player has pending request and permission (with lenient permission check)
     * @param source command source
     * @param permission required permission  
     * @return true if player has pending request and permission (or is basic user)
     */
    public static boolean requiresPendingRequestWithPermission(CommandSourceStack source, String permission) {
        if (!source.isPlayer()) {
            return false;
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            // Check if has pending request
            boolean hasPendingRequest = ManagerRegistry.getInstance().getManager(PeekRequestManager.class).getPendingRequestForPlayer(player.getUUID()) != null;
            // Use very lenient permission check - basic users (permission level 0) can use accept/deny
            boolean hasPermission = PermissionChecker.hasPermission(source, permission, 0);
            return hasPendingRequest && hasPermission;
        } catch (Exception e) {
            PeekMod.LOGGER.error(e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if player is peeking and has permission (with lenient permission check)
     * @param source command source
     * @param permission required permission
     * @return true if player is peeking and has permission (or is basic user)
     */
    public static boolean requiresActivePeekerWithPermission(CommandSourceStack source, String permission) {
        if (!source.isPlayer()) {
            return false;
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            // Check if is currently peeking
            boolean isPeeking = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).isPlayerPeeking(player.getUUID());
            // Use very lenient permission check - basic users (permission level 0) can use stop
            boolean hasPermission = PermissionChecker.hasPermission(source, permission, 0);
            return isPeeking && hasPermission;
        } catch (Exception e) {
            PeekMod.LOGGER.error(e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if player has blacklisted players (for blacklist remove command visibility)
     * @param source command source
     * @param permission required permission
     * @param opLevel minimum OP level
     * @return true if player has blacklisted players and permission
     */
    public static boolean requiresNonEmptyBlacklist(CommandSourceStack source, String permission, int opLevel) {
        if (!requiresPlayerWithPermission(source, permission, opLevel)) {
            return false;
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            PlayerPeekData data = eu.pb4.playerdata.api.PlayerDataApi.getCustomDataFor(player, 
                com.peek.data.PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
            return data != null && !data.blacklist().isEmpty();
        } catch (Exception e) {
            PeekMod.LOGGER.error(e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if player can send peek requests (for peek player command visibility)
     * Players can send requests even while peeking others (for peek switching)
     * Players cannot send requests while having pending outgoing requests
     * @param source command source
     * @param permission required permission
     * @param opLevel minimum OP level
     * @return true if player can send peek requests and has permission
     */
    public static boolean canSendPeekRequestWithPermission(CommandSourceStack source, String permission, int opLevel) {
        if (!requiresPlayerWithPermission(source, permission, opLevel)) {
            return false;
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            return canPlayerSendPeekRequest(player.getUUID());
        } catch (Exception e) {
            PeekMod.LOGGER.error(e.getMessage());
            return false;
        }
    }
    
    /**
     * @deprecated Use canSendPeekRequestWithPermission instead. Name was misleading.
     */
    @Deprecated
    public static boolean requiresNoPendingRequests(CommandSourceStack source, String permission, int opLevel) {
        return canSendPeekRequestWithPermission(source, permission, opLevel);
    }
    
    /**
     * Check if a player can send peek requests 
     * Players can send requests while peeking others (for switching) but not while having pending requests
     * @param playerId Player UUID
     * @return true if player can send peek requests
     */
    public static boolean canPlayerSendPeekRequest(java.util.UUID playerId) {
        // Players can send peek requests even while peeking others (for peek switching)
        // Players can send peek requests even while being peeked by others
        // But NOT while having pending outgoing requests (prevents spam/conflicts)
        return !ManagerRegistry.getInstance().getManager(PeekRequestManager.class).hasPendingRequestAsRequester(playerId);
    }
    
    /**
     * Check if a player can be peeked 
     * Player can be peeked while peeking others, but NOT during their own peek initiation (pending outgoing requests)
     * @param playerId Player UUID
     * @return true if player can be peeked
     */
    public static boolean canPlayerBePeeked(java.util.UUID playerId) {
        // Player can be peeked while peeking someone else
        // But NOT while having pending outgoing requests (during their own peek initiation)
        
        // Check if player has pending outgoing request - if so, they're in "peek initiation" mode
        if (ManagerRegistry.getInstance().getManager(PeekRequestManager.class).hasPendingRequestAsRequester(playerId)) {
            return false; // Player is trying to peek someone, don't allow being peeked during this time
        }
        
        // Check session limit per player
        int maxSessionsPerPlayer = ModConfigManager.getMaxPeekSessionsPerPlayer();
        if (maxSessionsPerPlayer > 0) {
            int currentSessions = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).getSessionsTargeting(playerId).size();
            if (currentSessions >= maxSessionsPerPlayer) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if player A can send a peek request to player B (no conflicts between them)
     * @param requesterId Requester UUID
     * @param targetId Target UUID  
     * @return true if request can be sent
     */
    public static boolean canSendPeekRequestTo(java.util.UUID requesterId, java.util.UUID targetId) {
        // Check if requester can send requests
        if (!canPlayerSendPeekRequest(requesterId)) {
            return false;
        }
        
        // Check if already peeking the same target
        if (ManagerRegistry.getInstance().getManager(PeekSessionManager.class).isPlayerPeeking(requesterId)) {
            UUID currentTargetId = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).getCurrentTarget(requesterId);
            if (currentTargetId != null && currentTargetId.equals(targetId)) {
                return false; // Can't peek the same target they're already peeking
            }
            // Allow peek switching to different targets
        }
        
        // Check if target can be peeked  
        if (!canPlayerBePeeked(targetId)) {
            return false;
        }
        
        // Check if there's already a request between them
        return !ManagerRegistry.getInstance().getManager(PeekRequestManager.class).hasPendingRequestBetween(requesterId, targetId);
    }
    
    /**
     * Check if requester can send a peek request to target (with player entities for private mode check)
     * @param requester Requester player entity
     * @param target Target player entity
     * @return true if request can be sent
     */
    public static boolean canSendPeekRequestTo(ServerPlayer requester, ServerPlayer target) {
        UUID requesterId = requester.getUUID();
        UUID targetId = target.getUUID();
        
        // Check if requester can send requests
        if (!canPlayerSendPeekRequest(requesterId)) {
            return false;
        }
        
        // Check if already peeking the same target
        if (ManagerRegistry.getInstance().getManager(PeekSessionManager.class).isPlayerPeeking(requesterId)) {
            UUID currentTargetId = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).getCurrentTarget(requesterId);
            if (currentTargetId != null && currentTargetId.equals(targetId)) {
                return false; // Can't peek the same target they're already peeking
            }
            // Allow peek switching to different targets
        }
        
        // Check if target is in private mode (unless requester can bypass)
        PlayerPeekData targetData = PlayerDataApi.getCustomDataFor(target, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
        if (targetData != null && targetData.privateMode() && 
            !canBypass(requester, com.peek.utils.permissions.Permissions.Bypass.PRIVATE_MODE, 2)) {
            return false; // Target is in private mode
        }
        
        // Check if target has requester blacklisted (unless requester can bypass)
        if (targetData != null && targetData.blacklist().containsKey(requesterId) &&
            !canBypass(requester, com.peek.utils.permissions.Permissions.Bypass.BLACKLIST, 2)) {
            return false; // Requester is blacklisted by target
        }
        
        // Check if target can be peeked  
        if (!canPlayerBePeeked(targetId)) {
            return false;
        }
        
        // Check if there's already a request between them
        return !ManagerRegistry.getInstance().getManager(PeekRequestManager.class).hasPendingRequestBetween(requesterId, targetId);
    }
    
    
    /**
     * Validates if it's safe to start peek (no hostile mobs around)
     * @param player The player who wants to start peek
     * @return true if safe or no check required, false if unsafe
     */
    public static boolean validateNoHostileMobsAround(ServerPlayer player) {
        double radius = ModConfigManager.getNoMobsRadius();
        if (radius <= 0 || PermissionChecker.isOp(player) || com.peek.utils.compat.PlayerCompat.getGameMode(player) == GameType.CREATIVE || com.peek.utils.compat.PlayerCompat.getGameMode(player) == GameType.SPECTATOR) {
            return true; // No check required
        }
        
        // Check if player has bypass permission for no_mobs check (default level: 2)
        if (PermissionChecker.hasPermission(player, com.peek.utils.permissions.Permissions.Bypass.NO_MOBS, 2)) {
            return true; // Player can bypass hostile mob checks
        }
        
        // Check for hostile mobs in radius around player
        BlockPos pos = player.blockPosition();
        AABB box = AABB.ofSize(net.minecraft.world.phys.Vec3.atCenterOf(pos), radius * 2, Math.min(radius, 5) * 2, radius * 2);

        // Search for hostile monsters
        return ServerPlayerCompat.getWorld(player).getEntitiesOfClass(Monster.class, box,
            (entity) -> entity.isAlive() && !entity.isRemoved()).isEmpty();
    }
    
    /**
     * Validates minimum distance between players in same dimension
     * @param requester The player requesting to peek
     * @param target The target player
     * @return true if distance is valid or players in different dimensions
     */
    public static boolean validateMinimumDistance(ServerPlayer requester, ServerPlayer target) {
        double minDistance = ModConfigManager.getMinSameDimensionDistance();
        if (minDistance <= 0 || PermissionChecker.isOp(requester)) {
            return true; // No distance limit
        }
        
        // If players are in different dimensions, skip distance check
        if (ServerPlayerCompat.getWorld(requester) != ServerPlayerCompat.getWorld(target)) {
            return true;
        }

        // Check distance in same dimension
        double distance = ServerPlayerCompat.getPos(requester).distanceTo(ServerPlayerCompat.getPos(target));
        return distance >= minDistance;
    }
}


