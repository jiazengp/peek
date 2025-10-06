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
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

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
    public void teleportPeekerToTarget(ServerPlayerEntity peeker, ServerPlayerEntity target) {
        try {
            Vec3d targetPos = ServerPlayerCompat.getPos(target);
            Vec3d peekerPos = ServerPlayerCompat.getPos(peeker);

            PeekMod.LOGGER.debug("Teleporting {} from {} to target {} at {}",
                ProfileCompat.getName(peeker.getGameProfile()), peekerPos, ProfileCompat.getName(target.getGameProfile()), targetPos);

            // Play teleport sound before teleportation
            SoundManager.playTeleportToTargetSound(peeker);

            LoggingHelper.logTeleportOperation("Executing", ProfileCompat.getName(peeker.getGameProfile()), targetPos);

            // Cross-dimensional or same-world teleport
            if (ServerPlayerCompat.getWorld(peeker) != ServerPlayerCompat.getWorld(target)) {
                // Cross-dimensional teleport
                PeekMod.LOGGER.debug("Cross-dimension spectator follow from {} to {}",
                    ServerPlayerCompat.getWorld(peeker).getRegistryKey().getValue(),
                    ServerPlayerCompat.getWorld(target).getRegistryKey().getValue());

                TeleportTarget teleportTarget = new TeleportTarget(com.peek.utils.compat.PlayerCompat.getServerWorld(target), 
                    targetPos, // Use exact target position for spectator
                    Vec3d.ZERO, target.getYaw(), target.getPitch(), 
                    (Entity entity) -> {});
                    
                PeekMod.LOGGER.debug("Executing cross-dimensional teleport with TeleportTarget");
                peeker.teleportTo(teleportTarget);
                PeekMod.LOGGER.debug("Cross-dimensional teleport completed");
            } else {
                // Same world teleport - use single reliable method
                PeekMod.LOGGER.debug("Executing same-world teleport to {}, {}, {}", 
                    targetPos.x, targetPos.y, targetPos.z);
                
                // Use standard teleport method with sync enabled
                peeker.teleport(targetPos.x, targetPos.y, targetPos.z, true);
                PeekMod.LOGGER.debug("Same-world teleport completed");
            }
            
            // Verify teleportation success
            Vec3d newPeekerPos = ServerPlayerCompat.getPos(peeker);
            Vec3d currentTargetPos = ServerPlayerCompat.getPos(target);
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
    public boolean handlePeekerDistanceExceeded(ServerPlayerEntity peeker, ServerPlayerEntity target, PeekSession session) {
        if (ModConfigManager.shouldTeleportBackOnDistanceExceeded()) {
            // Teleport peeker back to target (sound will be played in teleportPeekerToTarget method)
            teleportPeekerToTarget(peeker, target);
            
            Text message = MessageBuilder.warning("peek.message.teleported_back_distance");
            peeker.sendMessage(message, false);

            PeekMod.LOGGER.debug("Teleported peeker {} back to target due to distance exceeded",
                ProfileCompat.getName(peeker.getGameProfile()));
            return false; // Continue session after teleporting back
        } else if (ModConfigManager.shouldEndPeekOnDistanceExceeded()) {
            // End the peek session
            Text message = MessageBuilder.message("peek.message.ended_distance");
            peeker.sendMessage(message, false);

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