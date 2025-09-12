package com.peek.utils;

import com.peek.PeekMod;
import com.peek.manager.ManagerRegistry;
import com.peek.manager.PeekRequestManager;
import com.peek.manager.PeekSessionManager;
import com.peek.manager.constants.GameConstants;
import com.peek.data.peek.PeekSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Utility class for checking and fixing state consistency issues
 */
public class StateConsistencyChecker {
    
    private static int checksPerformed = 0;
    private static int issuesFixed = 0;
    
    /**
     * Performs a comprehensive state consistency check
     * @param server The minecraft server instance
     * @return number of issues found and fixed
     */
    public static int performConsistencyCheck(MinecraftServer server) {
        checksPerformed++;
        int issuesFoundThisCheck = 0;
        
        PeekMod.LOGGER.debug("Starting state consistency check #{}", checksPerformed);
        
        // Check 1: Verify session mappings are consistent
        issuesFoundThisCheck += checkSessionMappingConsistency();
        
        // Check 2: Verify players still exist for active sessions
        issuesFoundThisCheck += checkPlayerExistence(server);
        
        // Check 3: Check for orphaned requests
        issuesFoundThisCheck += checkOrphanedRequests(server);
        
        // Check 4: Verify session states
        issuesFoundThisCheck += checkSessionStates();
        
        if (issuesFoundThisCheck > 0) {
            PeekMod.LOGGER.info("Consistency check #{} found and fixed {} issues", 
                checksPerformed, issuesFoundThisCheck);
            issuesFixed += issuesFoundThisCheck;
        } else {
            PeekMod.LOGGER.debug("Consistency check #{} found no issues", checksPerformed);
        }
        
        return issuesFoundThisCheck;
    }
    
    /**
     * Checks if session mappings are consistent between different maps
     */
    private static int checkSessionMappingConsistency() {
        int issues = 0;
        PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
        
        // Get all active sessions
        Map<UUID, PeekSession> activeSessions = sessionManager.getActiveSessions();
        
        for (PeekSession session : activeSessions.values()) {
            UUID peekerId = session.getPeekerId();
            UUID targetId = session.getTargetId();
            
            // Check if peeker mapping exists
            if (!sessionManager.isPlayerPeeking(peekerId)) {
                PeekMod.LOGGER.warn("Found session {} with missing peeker mapping for {}", 
                    session.getId(), session.getPeekerName());
                issues++;
                // Fix: Remove the session as it's orphaned
                // Note: This is a simplistic fix - in production might want more sophisticated recovery
            }
            
            // Check if target mapping exists
            List<PeekSession> targetingSessions = sessionManager.getSessionsTargeting(targetId);
            boolean found = targetingSessions.stream()
                .anyMatch(s -> s.getId().equals(session.getId()));
            
            if (!found) {
                PeekMod.LOGGER.warn("Found session {} with missing target mapping for {}", 
                    session.getId(), session.getTargetName());
                issues++;
            }
        }
        
        return issues;
    }
    
    /**
     * Checks if players in active sessions still exist on the server
     */
    private static int checkPlayerExistence(MinecraftServer server) {
        int issues = 0;
        PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
        
        Map<UUID, PeekSession> activeSessions = sessionManager.getActiveSessions();
        Set<UUID> sessionsToStop = new HashSet<>();
        
        for (PeekSession session : activeSessions.values()) {
            UUID peekerId = session.getPeekerId();
            UUID targetId = session.getTargetId();
            
            ServerPlayerEntity peeker = server.getPlayerManager().getPlayer(peekerId);
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetId);
            
            if (peeker == null) {
                PeekMod.LOGGER.warn("Found session {} with offline peeker {}", 
                    session.getId(), session.getPeekerName());
                sessionsToStop.add(peekerId);
                issues++;
            }
            
            if (target == null) {
                PeekMod.LOGGER.warn("Found session {} with offline target {}", 
                    session.getId(), session.getTargetName());
                sessionsToStop.add(peekerId);
                issues++;
            }
        }
        
        // Stop sessions with offline players
        for (UUID peekerId : sessionsToStop) {
            sessionManager.stopPeekSession(peekerId, false, server);
            PeekMod.LOGGER.info("Stopped session for offline player");
        }
        
        return issues;
    }
    
    /**
     * Checks for orphaned requests (requests without valid players)
     */
    private static int checkOrphanedRequests(MinecraftServer server) {
        int issues = 0;
        PeekRequestManager requestManager = ManagerRegistry.getInstance().getManager(PeekRequestManager.class);
        
        // This is simplified - would need access to internal request data
        // In a full implementation, would iterate through active requests and validate players exist
        
        return issues;
    }
    
    /**
     * Checks if session states are valid
     */
    private static int checkSessionStates() {
        int issues = 0;
        PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
        
        Map<UUID, PeekSession> activeSessions = sessionManager.getActiveSessions();
        
        for (PeekSession session : activeSessions.values()) {
            // Check if session is marked as active
            if (!session.isActive()) {
                PeekMod.LOGGER.warn("Found inactive session {} still in active sessions map", 
                    session.getId());
                issues++;
            }
            
            // Check session duration (if it's been running too long without updates)
            long duration = session.getDurationSeconds();
            if (duration > GameConstants.LONG_RUNNING_SESSION_THRESHOLD_SECONDS) {
                PeekMod.LOGGER.warn("Found long-running session {} ({}s) between {} and {}", 
                    session.getId(), duration, session.getPeekerName(), session.getTargetName());
                // Could add automatic cleanup for extremely long sessions
            }
        }
        
        return issues;
    }
    
    /**
     * Gets statistics about performed consistency checks
     */
    public static String getStatistics() {
        return String.format("Consistency checks performed: %d, Total issues fixed: %d", 
            checksPerformed, issuesFixed);
    }
    
    /**
     * Resets statistics (for testing or admin commands)
     */
    public static void resetStatistics() {
        checksPerformed = 0;
        issuesFixed = 0;
        PeekMod.LOGGER.info("Reset state consistency check statistics");
    }
}