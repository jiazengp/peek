package com.peek.manager.session;

import com.peek.PeekMod;
import com.peek.config.ModConfigManager;
import com.peek.data.peek.PeekSession;
import com.peek.utils.MessageBuilder;
import com.peek.utils.compat.ServerPlayerCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Handles session updates and validation logic
 */
public class SessionUpdateHandler {
    
    private final TeleportationManager teleportationManager;
    
    public SessionUpdateHandler(TeleportationManager teleportationManager) {
        this.teleportationManager = teleportationManager;
    }
    
    /**
     * Updates a single session for distance checking and state validation
     */
    public boolean updateSessionChecks(PeekSession session, MinecraftServer server, 
                                     SessionEndCallback sessionEndCallback) {
        try {
            UUID peekerId = session.getPeekerId();
            UUID targetId = session.getTargetId();
            
            if (server == null) {
                return true; // Continue processing other sessions
            }
            
            // Skip checks for recently created sessions to allow teleportation to complete
            long sessionAgeSeconds = java.time.Duration.between(session.getStartTime(), java.time.Instant.now()).getSeconds();
            if (sessionAgeSeconds < 3) { // Skip checks for first 3 seconds
                PeekMod.LOGGER.debug("Skipping checks for recent session {} (age: {}s)", session.getPeekerName(), sessionAgeSeconds);
                return true; 
            }
            
            ServerPlayerEntity peeker = server.getPlayerManager().getPlayer(peekerId);
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetId);
            
            // Check if either player is offline
            if (peeker == null || target == null) {
                PeekMod.LOGGER.debug("Player offline, ending peek session: peeker={}, target={}", 
                    session.getPeekerName(), session.getTargetName());
                sessionEndCallback.endSession(peekerId, false);
                return true;
            }
            
            // Update positions
            Vec3d targetPos = ServerPlayerCompat.getPos(target);
            Vec3d peekerPos = ServerPlayerCompat.getPos(peeker);
            UUID targetWorldId = UUID.nameUUIDFromBytes(
                ServerPlayerCompat.getWorld(target).getRegistryKey().getValue().toString().getBytes()
            );
            UUID currentTargetWorldId = session.getCurrentWorldId();
            
            // Check if target has changed dimensions
            boolean targetChangedDimension = currentTargetWorldId != null && !targetWorldId.equals(currentTargetWorldId);
            
            if (targetChangedDimension) {
                return handleDimensionChange(session, peeker, target, targetWorldId, 
                    currentTargetWorldId, sessionEndCallback);
            }
            
            // Only update world ID if no dimension change occurred
            session.updateTargetPosition(targetPos, targetWorldId);
            session.updatePeekerPosition(peekerPos);
            
            // Only check distance if players are in the same dimension
            if (ServerPlayerCompat.getWorld(peeker) != ServerPlayerCompat.getWorld(target)) {
                return true; // Different dimensions, skip distance checks
            }
            
            return performDistanceChecks(session, peeker, target, targetPos, peekerPos, sessionEndCallback);
            
        } catch (Exception e) {
            PeekMod.LOGGER.error("Error in updateSessionChecks", e);
            return true; // Continue processing other sessions
        }
    }
    
    /**
     * Handles dimension change scenarios
     */
    private boolean handleDimensionChange(PeekSession session, ServerPlayerEntity peeker, ServerPlayerEntity target,
                                        UUID targetWorldId, UUID currentTargetWorldId, 
                                        SessionEndCallback sessionEndCallback) {
        PeekMod.LOGGER.debug("Target {} changed dimension from {} to {}", 
            session.getTargetName(), currentTargetWorldId, targetWorldId);
            
        if (!ModConfigManager.isAllowDimensionFollowing()) {
            PeekMod.LOGGER.debug("Dimension following not allowed, ending peek session");
            Text message = MessageBuilder.message("peek.message.ended_dimension_change");
            peeker.sendMessage(message, false);
            sessionEndCallback.endSession(session.getPeekerId(), false);
            return true;
        } else {
            // Schedule teleportation after configured delay to give portal time to settle
            int delayTicks = ModConfigManager.getDimensionFollowDelayTicks();
            PeekMod.LOGGER.debug("Scheduling delayed teleportation ({} ticks) to follow target {} to new dimension", 
                delayTicks, session.getTargetName());
            
            teleportationManager.scheduleDelayedTeleport(
                session.getPeekerId(), 
                session.getTargetId(),
                session.getId(),
                session.getTargetName(),
                delayTicks
            );
        }
        
        return true;
    }
    
    /**
     * Performs distance validation checks
     */
    private boolean performDistanceChecks(PeekSession session, ServerPlayerEntity peeker, ServerPlayerEntity target,
                                        Vec3d targetPos, Vec3d peekerPos, SessionEndCallback sessionEndCallback) {
        // Check distance limits for peeker movement
        double maxMoveDistance = ModConfigManager.getMaxPeekMoveDistance();
        if (maxMoveDistance > 0) {
            double distanceFromTarget = session.getDistanceFromTarget();
            
            PeekMod.LOGGER.debug("Distance check for {}: distanceFromTarget={}, maxMoveDistance={}", 
                session.getPeekerName(), distanceFromTarget, maxMoveDistance);
                
            if (distanceFromTarget > maxMoveDistance) {
                PeekMod.LOGGER.debug("Peeker {} exceeded move distance limit: {} > {}", 
                    session.getPeekerName(), distanceFromTarget, maxMoveDistance);
                    
                boolean shouldEndSession = teleportationManager.handlePeekerDistanceExceeded(peeker, target, session);
                if (shouldEndSession) {
                    sessionEndCallback.endSession(session.getPeekerId(), false);
                }
                return true; // Continue processing
            }
        }
        
        // Check if target player has moved too far from peeker's original position
        // Skip this check for cross-dimensional scenarios
        UUID originalWorldId = session.getOriginalWorldId();
        UUID currentTargetWorldId = UUID.nameUUIDFromBytes(
            ServerPlayerCompat.getWorld(target).getRegistryKey().getValue().toString().getBytes()
        );
        
        boolean isCrossDimensional = !originalWorldId.equals(currentTargetWorldId);
        double maxDistance = ModConfigManager.getMaxDistance();
        
        if (maxDistance > 0 && !isCrossDimensional && targetPos.distanceTo(session.getOriginalPeekerState().position()) > maxDistance) {
            PeekMod.LOGGER.info("Target moved too far from original peek location, ending peek session");
            sessionEndCallback.endSession(session.getPeekerId(), false);
            return true;
        } else if (isCrossDimensional) {
            PeekMod.LOGGER.debug("Skipping distance check for cross-dimensional peek session");
        }
        
        // Check if peeker player has moved too far from target (this is the main distance limit)
        double peekerToTargetDistance = peekerPos.distanceTo(targetPos);
        double maxPeekerDistance = ModConfigManager.getMaxDistance();
        
        PeekMod.LOGGER.debug("Distance check - Peeker to target: {}, Max allowed: {}", 
            peekerToTargetDistance, maxPeekerDistance);
        
        if (maxPeekerDistance > 0 && peekerToTargetDistance > maxPeekerDistance) {
            PeekMod.LOGGER.info("Peeker moved too far from target ({}>{} blocks), ending peek session", 
                peekerToTargetDistance, maxPeekerDistance);
            sessionEndCallback.endSession(session.getPeekerId(), false);
            return true;
        }
        
        return true;
    }
    
    /**
     * Callback interface for ending sessions
     */
    public interface SessionEndCallback {
        void endSession(UUID peekerId, boolean voluntary);
    }
}