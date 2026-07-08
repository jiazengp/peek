package com.peek.manager.session;

import com.peek.PeekMod;
import com.peek.config.ModConfigManager;
import com.peek.data.peek.PeekSession;
import com.peek.manager.exceptions.TeleportationException;
import com.peek.utils.LoggingHelper;
import com.peek.utils.MessageBuilder;
import com.peek.utils.SoundManager;
import com.peek.utils.compat.ProfileCompat;
import com.peek.utils.compat.ServerPlayerCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.TeleportTransition;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles teleportation logic for peek sessions
 */
public class TeleportationManager {
    
    /**
     * Delayed teleportation task for cross-dimensional following
     */
    public static class DelayedTeleportTask {
        public final UUID peekerId;
        public final UUID targetId;
        public final UUID sessionId;
        public final String targetName;
        public int remainingTicks;
        
        public DelayedTeleportTask(UUID peekerId, UUID targetId, UUID sessionId, String targetName, int delayTicks) {
            this.peekerId = peekerId;
            this.targetId = targetId;
            this.sessionId = sessionId;
            this.targetName = targetName;
            this.remainingTicks = delayTicks;
        }
        
        public boolean tick() {
            return --remainingTicks <= 0;
        }
    }
    
    private final List<DelayedTeleportTask> pendingTeleports = new ArrayList<>();
    
    /**
     * Teleports spectator peeker to target's exact real-time position for accurate following
     */
    public void teleportPeekerToTarget(ServerPlayer peeker, ServerPlayer target) {
        try {
            Vec3 targetPos = ServerPlayerCompat.getPos(target);
            Vec3 peekerPos = ServerPlayerCompat.getPos(peeker);

            PeekMod.LOGGER.debug("Teleporting {} from {} to target {} at {}",
                ProfileCompat.getName(peeker.getGameProfile()), peekerPos, ProfileCompat.getName(target.getGameProfile()), targetPos);

            // Play teleport sound before teleportation
            SoundManager.playTeleportToTargetSound(peeker);

            LoggingHelper.logTeleportOperation("Executing", ProfileCompat.getName(peeker.getGameProfile()), targetPos);

            // Cross-dimensional or same-world teleport
            if (ServerPlayerCompat.getWorld(peeker) != ServerPlayerCompat.getWorld(target)) {
                // Cross-dimensional teleport
                PeekMod.LOGGER.debug("Cross-dimension spectator follow from {} to {}",
                    ServerPlayerCompat.getWorld(peeker).dimension().identifier(),
                    ServerPlayerCompat.getWorld(target).dimension().identifier());

                TeleportTransition teleportTarget = new TeleportTransition(com.peek.utils.compat.PlayerCompat.getServerWorld(target), 
                    targetPos, // Use exact target position for spectator
                    Vec3.ZERO, target.getYRot(), target.getXRot(), 
                    TeleportTransition.DO_NOTHING);
                    
                PeekMod.LOGGER.debug("Executing cross-dimensional teleport with TeleportTarget");
                peeker.teleport(teleportTarget);
                PeekMod.LOGGER.debug("Cross-dimensional teleport completed");
            } else {
                // Same world teleport - use single reliable method
                PeekMod.LOGGER.debug("Executing same-world teleport to {}, {}, {}", 
                    targetPos.x, targetPos.y, targetPos.z);
                
                // Use standard teleport method with sync enabled
                peeker.teleportTo(targetPos.x, targetPos.y, targetPos.z);
                PeekMod.LOGGER.debug("Same-world teleport completed");
            }
            
            // Verify teleportation success
            Vec3 newPeekerPos = ServerPlayerCompat.getPos(peeker);
            Vec3 currentTargetPos = ServerPlayerCompat.getPos(target);
            double distance = newPeekerPos.distanceTo(currentTargetPos);

            if (distance > 5.0) {
                PeekMod.LOGGER.warn("Teleportation verification failed - distance to target is {} blocks", distance);
            } else {
                PeekMod.LOGGER.debug("Teleportation successful - peeker {} at distance {} from target {}",
                    ProfileCompat.getName(peeker.getGameProfile()), distance, ProfileCompat.getName(target.getGameProfile()));
            }
                
        } catch (Exception e) {
            throw new TeleportationException(
                TeleportationException.TeleportOperation.SAME_WORLD_TELEPORT,
                "TELEPORT_FAILED",
                "Failed to teleport peeker to target: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Handles when peeker exceeds allowed movement distance from target
     * @return true if session should be ended, false otherwise
     */
    public boolean handlePeekerDistanceExceeded(ServerPlayer peeker, ServerPlayer target, PeekSession session) {
        if (ModConfigManager.shouldTeleportBackOnDistanceExceeded()) {
            // Teleport peeker back to target (sound will be played in teleportPeekerToTarget method)
            teleportPeekerToTarget(peeker, target);
            
            Component message = MessageBuilder.warning("peek.message.teleported_back");
            peeker.sendSystemMessage(message, false);

            PeekMod.LOGGER.debug("Teleported peeker {} back to target due to distance exceeded",
                ProfileCompat.getName(peeker.getGameProfile()));
            return false; // Continue session after teleporting back
        } else if (ModConfigManager.shouldEndPeekOnDistanceExceeded()) {
            PeekMod.LOGGER.debug("Ending peek session due to peeker {} exceeding distance limit",
                ProfileCompat.getName(peeker.getGameProfile()));
            return true; // End session
        }
        return false; // Default: continue session
    }
    
    /**
     * Schedules a delayed teleportation task
     */
    public void scheduleDelayedTeleport(UUID peekerId, UUID targetId, UUID sessionId, String targetName, int delayTicks) {
        // Check if already scheduled outside of lock for better performance
        boolean alreadyScheduled;
        synchronized (pendingTeleports) {
            alreadyScheduled = pendingTeleports.stream()
                .anyMatch(task -> task.sessionId.equals(sessionId));
        }
        
        if (alreadyScheduled) {
            PeekMod.LOGGER.debug("Delayed teleport already scheduled for session {}, skipping", sessionId);
            return;
        }
        
        DelayedTeleportTask teleportTask = new DelayedTeleportTask(
            peekerId, targetId, sessionId, targetName, delayTicks
        );
        
        synchronized (pendingTeleports) {
            // Double-check after acquiring lock
            boolean stillNotScheduled = pendingTeleports.stream()
                .noneMatch(task -> task.sessionId.equals(sessionId));
            
            if (stillNotScheduled) {
                pendingTeleports.add(teleportTask);
                PeekMod.LOGGER.debug("Scheduled delayed teleport task for {} ticks (session: {})", delayTicks, sessionId);
            } else {
                PeekMod.LOGGER.debug("Teleport task was scheduled by another thread, skipping");
            }
        }
    }
    
    /**
     * Processes all pending delayed teleportation tasks
     */
    public List<DelayedTeleportTask> processPendingTeleports() {
        List<DelayedTeleportTask> completedTasks = new ArrayList<>();
        
        synchronized (pendingTeleports) {
            if (pendingTeleports.isEmpty()) {
                return completedTasks;
            }
            
            for (DelayedTeleportTask task : pendingTeleports) {
                if (task.tick()) {
                    completedTasks.add(task);
                }
            }
            
            // Remove completed tasks
            pendingTeleports.removeAll(completedTasks);
        }
        
        return completedTasks;
    }
    
    /**
     * Clears all pending teleportation tasks
     */
    public void clearPendingTeleports() {
        synchronized (pendingTeleports) {
            pendingTeleports.clear();
        }
    }
    
    /**
     * Gets the number of pending teleportation tasks
     */
    public int getPendingTeleportsCount() {
        synchronized (pendingTeleports) {
            return pendingTeleports.size();
        }
    }
}


