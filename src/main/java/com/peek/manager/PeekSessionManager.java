package com.peek.manager;

import com.peek.PeekMod;
import com.peek.config.ModConfigManager;
import com.peek.data.PeekDataStorage;
import com.peek.data.peek.PeekSession;
import com.peek.data.peek.PlayerPeekData;
import com.peek.data.peek.PlayerState;
import com.peek.manager.constants.ErrorCodes;
import com.peek.manager.constants.GameConstants;
import com.peek.manager.constants.PeekConstants;
import com.peek.manager.exceptions.TeleportationException;
import com.peek.manager.session.SessionCreationContext;
import com.peek.manager.session.SessionUpdateHandler;
import com.peek.manager.session.TeleportationManager;
import com.peek.utils.*;
import com.peek.utils.compat.ProfileCompat;
import com.peek.utils.compat.ServerPlayerCompat;
import com.peek.utils.permissions.Permissions;
import eu.pb4.playerdata.api.PlayerDataApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active peek sessions
 */
public class PeekSessionManager extends BaseManager {
    
    // DelayedTeleportTask is now handled by TeleportationManager
    private final Map<UUID, PeekSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> peekerToSession = new ConcurrentHashMap<>();  // peeker -> session id
    private final Map<UUID, Set<UUID>> targetToSession = new ConcurrentHashMap<>();   // target -> session ids
    
    // Anti-ping-pong mechanism: track recent circular peek swaps
    private final Map<String, Long> recentCircularPeeks = new ConcurrentHashMap<>(); // "playerA-playerB" -> timestamp
    // server is now inherited from BaseManager

    // Injected dependencies
    private final PeekRequestManager requestManager;
    private final PlayerStateManager playerStateManager;

    // Separated components for better architecture
    private final TeleportationManager teleportationManager = new TeleportationManager();
    private final SessionUpdateHandler sessionUpdateHandler = new SessionUpdateHandler(teleportationManager);
    
    // Unified tick task management and counters
    private final TickTaskManager tickTaskManager = new TickTaskManager();
    private int tickCounter = 0;
    private int cleanupTickCounter = 0;
    
    
    // Task type constants
    private static final String TASK_TYPE_SESSION_TIMEOUT = "session_timeout";
    
    public PeekSessionManager(PeekRequestManager requestManager, PlayerStateManager playerStateManager) {
        this.requestManager = requestManager;
        this.playerStateManager = playerStateManager;
        // Cleanup is now handled by tick system - no need for separate scheduler
    }
    
    /**
     * Starts a peek session between two players
     * Synchronized to prevent race conditions during session creation/switching
     */
    public synchronized PeekConstants.Result<PeekSession> startPeekSession(ServerPlayerEntity peeker, ServerPlayerEntity target) {
        SessionCreationContext context = new SessionCreationContext(peeker, target);
        try {
            // Step 1: Initial cleanup and validation
            PeekConstants.Result<String> cleanupResult = performInitialCleanup(context);
            if (!cleanupResult.isSuccess()) {
                return PeekConstants.Result.failure(cleanupResult.getError());
            }
            
            // Step 2: Handle peek switching scenario
            PeekConstants.Result<String> switchingResult = handlePeekSwitching(context);
            if (!switchingResult.isSuccess()) {
                return PeekConstants.Result.failure(switchingResult.getError());
            }
            
            // Step 3: Validate session preconditions
            PeekConstants.Result<String> validationResult = validateSessionPreconditions(context);
            if (!validationResult.isSuccess()) {
                return PeekConstants.Result.failure(validationResult.getError());
            }
            
            // Step 4: Prepare player state
            PeekConstants.Result<String> stateResult = preparePlayerState(context);
            if (!stateResult.isSuccess()) {
                return PeekConstants.Result.failure(stateResult.getError());
            }
            
            // Step 5: Create and register session
            PeekConstants.Result<String> creationResult = createAndRegisterSession(context);
            if (!creationResult.isSuccess()) {
                return PeekConstants.Result.failure(creationResult.getError());
            }
            
            // Step 6: Execute teleportation and finalize
            PeekConstants.Result<String> finalizationResult = executeTeleportationAndFinalize(context);
            if (!finalizationResult.isSuccess()) {
                return PeekConstants.Result.failure(finalizationResult.getError());
            }
            
            return PeekConstants.Result.success(context.getCreatedSession());
            
        } catch (TeleportationException e) {
            PeekConstants.Result<String> errorResult = ExceptionHandler.handleTeleportationException(e, "startPeekSession",
                ProfileCompat.getName(context.getPeeker().getGameProfile()), ProfileCompat.getName(context.getTarget().getGameProfile()));
            return PeekConstants.Result.failure(errorResult.getError());
        } catch (Exception e) {
            PeekConstants.Result<String> errorResult = ExceptionHandler.handleSessionException(e, "startPeekSession",
                ProfileCompat.getName(context.getPeeker().getGameProfile()), ProfileCompat.getName(context.getTarget().getGameProfile()));
            return PeekConstants.Result.failure(errorResult.getError());
        }
    }
    
    /**
     * Step 1: Perform initial cleanup and validation
     */
    private PeekConstants.Result<String> performInitialCleanup(SessionCreationContext context) {
        String cleanupError = SessionUtils.handleTargetingSessionCleanup(
            context.getPeekerId(), context.getTargetId(), context.getPeeker(), 
            targetToSession, activeSessions, recentCircularPeeks, 
            (peekerId, voluntary, server) -> {
                PeekConstants.Result<String> result = stopPeekSession(peekerId, voluntary, server);
                if (!result.isSuccess()) {
                    PeekMod.LOGGER.warn("Failed to stop session during cleanup: {}", result.getError());
                }
            }
        );
        if (cleanupError != null) {
            return PeekConstants.Result.failure(cleanupError);
        }
        return PeekConstants.Result.success("Initial cleanup completed");
    }
    
    /**
     * Step 2: Handle peek switching scenario
     */
    private PeekConstants.Result<String> handlePeekSwitching(SessionCreationContext context) {
        if (isPlayerPeeking(context.getPeekerId())) {
            context.setWasSwitching(true);
            
            // Get current session data before making changes
            UUID currentSessionId = peekerToSession.get(context.getPeekerId());
            PeekSession currentSession = activeSessions.get(currentSessionId);
            if (currentSession != null) {
                context.setExistingOriginalState(currentSession.getOriginalPeekerState());
                context.setCurrentSessionToRestore(currentSession);
            }
            
            // Pre-validate conditions before stopping current session
            if (isPlayerBeingPeeked(context.getTargetId())) {
                return PeekConstants.Result.failure(ErrorCodes.BEING_PEEKED);
            }
            
            // Notify previous target about the switch
            notifyPreviousTargetOfSwitch(currentSession, context.getTarget());
            
            // Stop current session
            PeekConstants.Result<String> stopResult = stopPeekSessionWithoutRestore(context.getPeekerId());
            if (!stopResult.isSuccess()) {
                return PeekConstants.Result.failure(
                    Text.translatable("peek.error.failed_to_stop_session", stopResult.getError()).getString()
                );
            }
        } else {
            // Not switching - validate target is not being peeked
            if (isPlayerBeingPeeked(context.getTargetId())) {
                return PeekConstants.Result.failure(ErrorCodes.BEING_PEEKED);
            }
        }
        return PeekConstants.Result.success("Switching handled");
    }
    
    /**
     * Step 3: Validate session preconditions
     */
    private PeekConstants.Result<String> validateSessionPreconditions(SessionCreationContext context) {
        // Check if peeker is stationary
        if (!isPlayerStationary(context.getPeeker())) {
            return PeekConstants.Result.failure(ErrorCodes.PLAYER_NOT_STATIONARY);
        }
        
        // Check session limit
        if (activeSessions.size() >= ModConfigManager.getMaxActiveSessions()) {
            return PeekConstants.Result.failure(ErrorCodes.SESSION_LIMIT_EXCEEDED);
        }
        
        return PeekConstants.Result.success("Preconditions validated");
    }
    
    /**
     * Step 4: Prepare player state
     */
    private PeekConstants.Result<String> preparePlayerState(SessionCreationContext context) {
        PlayerState originalState = context.determineOriginalState();
        UUID originalWorldId = context.determineOriginalWorldId();
        
        context.setOriginalWorldId(originalWorldId);
        
        if (context.getExistingOriginalState() != null) {
            PeekMod.LOGGER.debug("Switching peek for player {}: preserving existing original state",
                ProfileCompat.getName(context.getPeeker().getGameProfile()));
        } else {
            PeekMod.LOGGER.debug("Starting new peek for player {}: saving original state for crash recovery",
                ProfileCompat.getName(context.getPeeker().getGameProfile()));
            
            // The originalState is already captured by determineOriginalState() above
            // We need to save this ORIGINAL state (not current state) to persistent storage
            PeekConstants.Result<String> saveResult = saveOriginalStateToPersistent(context.getPeeker(), originalState);
            
            if (!saveResult.isSuccess()) {
                PeekMod.LOGGER.error("Failed to save original state for crash recovery: {}", saveResult.getError());
                return PeekConstants.Result.failure("Failed to save original state for crash recovery: " + saveResult.getError());
            }
            
            PeekMod.LOGGER.debug("Successfully saved original state for crash recovery for player {}",
                ProfileCompat.getName(context.getPeeker().getGameProfile()));
        }
        
        return PeekConstants.Result.success("Player state prepared");
    }
    
    /**
     * Saves the original player state to persistent storage for crash recovery
     */
    private PeekConstants.Result<String> saveOriginalStateToPersistent(ServerPlayerEntity player, PlayerState originalState) {
        try {
            // Get or create player data
            PlayerPeekData playerData = com.peek.data.peek.PlayerPeekData.getOrCreate(player);
            
            // Save the original state to persistent storage
            playerData = playerData.withSavedState(originalState);
            PlayerDataApi.setCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, playerData);
            
            PeekMod.LOGGER.debug("Saved original state to PlayerDataAPI for player {} - pos={}, gamemode={}",
                ProfileCompat.getName(player.getGameProfile()), originalState.position(), originalState.gameMode());
            
            // PlayerDataAPI will automatically save this data for online players
            
            // Immediately verify the save by reading it back
            PlayerPeekData verifyData = com.peek.data.peek.PlayerPeekData.getOrCreate(player);
            if (verifyData.hasSavedState()) {
                PeekMod.LOGGER.debug("Verification: State save confirmed - data exists in PlayerDataAPI");
                PeekMod.LOGGER.debug("Verified saved position: {}", verifyData.savedState().position());
            } else {
                PeekMod.LOGGER.error("Verification FAILED: State was not saved correctly!");
            }
            
            return PeekConstants.Result.success("Original state saved");
            
        } catch (Exception e) {
            PeekMod.LOGGER.error("Error saving original state to persistent storage for {}",
                ProfileCompat.getName(player.getGameProfile()), e);
            return PeekConstants.Result.failure("Failed to save state: " + e.getMessage());
        }
    }
    
    /**
     * Step 5: Create and register session
     */
    private PeekConstants.Result<String> createAndRegisterSession(SessionCreationContext context) {
        PlayerState originalState = context.determineOriginalState();
        
        // Create session
        PeekSession session = new PeekSession(
            context.getPeekerId(), context.getTargetId(),
            ProfileCompat.getName(context.getPeeker().getGameProfile()),
            ProfileCompat.getName(context.getTarget().getGameProfile()),
            originalState,
            context.getOriginalWorldId()
        );
        
        // Store session mappings
        activeSessions.put(session.getId(), session);
        peekerToSession.put(context.getPeekerId(), session.getId());
        targetToSession.computeIfAbsent(context.getTargetId(), 
            k -> ConcurrentHashMap.newKeySet()).add(session.getId());
        
        // Save state for crash recovery
        PlayerPeekData peekerData = PlayerDataApi.getCustomDataFor(context.getPeeker(), 
            PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
        if (peekerData == null) {
            peekerData = PlayerPeekData.createDefault();
        }
        peekerData = peekerData.withSavedState(originalState);
        PlayerDataApi.setCustomDataFor(context.getPeeker(), 
            PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, peekerData);
        
        context.setCreatedSession(session);
        return PeekConstants.Result.success("Session created and registered");
    }
    
    /**
     * Step 6: Execute teleportation and finalize session
     */
    private PeekConstants.Result<String> executeTeleportationAndFinalize(SessionCreationContext context) {
        PeekSession session = context.getCreatedSession();
        ServerPlayerEntity peeker = context.getPeeker();
        ServerPlayerEntity target = context.getTarget();
        
        // Transform peeker to spectator mode
        peeker.changeGameMode(GameMode.SPECTATOR);
        peeker.clearStatusEffects();
        
        try {
            // Execute teleportation
            PeekMod.LOGGER.debug("Starting teleportation for {} to target {}",
                ProfileCompat.getName(peeker.getGameProfile()), ProfileCompat.getName(target.getGameProfile()));

            teleportationManager.teleportPeekerToTarget(peeker, target);

            // Update session with initial target position
            Vec3d targetPos = ServerPlayerCompat.getPos(target);
            session.updateTargetPosition(targetPos, UUID.nameUUIDFromBytes(
                ServerPlayerCompat.getWorld(target).getRegistryKey().getValue().toString().getBytes()
            ));

            PeekMod.LOGGER.debug("Session initialized successfully for {} peeking {}",
                ProfileCompat.getName(peeker.getGameProfile()), ProfileCompat.getName(target.getGameProfile()));
                
        } catch (Exception teleportError) {
            PeekMod.LOGGER.error("Teleportation failed for {} to {}: {}",
                ProfileCompat.getName(peeker.getGameProfile()), ProfileCompat.getName(target.getGameProfile()),
                teleportError.getMessage(), teleportError);
            
            // Rollback on teleport failure
            SessionUtils.handleSessionRollback(
                context.getPeekerId(), context.getTargetId(), session.getId(), 
                context.isWasSwitching(), context.getCurrentSessionToRestore(),
                activeSessions, peekerToSession, targetToSession, peeker,
                createRollbackHandler(context.getExistingOriginalState())
            );
            
            return PeekConstants.Result.failure(ErrorCodes.TELEPORT_FAILED);
        }
        
        // Send notifications
        SessionUtils.sendSessionStartNotifications(peeker, target, context.getExistingOriginalState() != null);
        
        // Schedule auto-stop if needed
        scheduleSessionTimeout(peeker, session);
        
        // Update command trees and effects
        com.peek.utils.CommandUtils.updateCommandTree(peeker);
        com.peek.utils.CommandUtils.updateCommandTree(target);
        LoggingHelper.logSessionOperation("Started", ProfileCompat.getName(peeker.getGameProfile()), ProfileCompat.getName(target.getGameProfile()));
        ParticleEffectManager.addPlayer(context.getPeekerId(), context.getTargetId());
        
        return PeekConstants.Result.success("Session finalized");
    }
    
    /**
     * Helper method to schedule session timeout
     */
    private void scheduleSessionTimeout(ServerPlayerEntity peeker, PeekSession session) {
        long maxDuration = ModConfigManager.getMaxSessionDuration();
        if (maxDuration > 0 && !ValidationUtils.canBypass(peeker, Permissions.Bypass.TIME_LIMIT, 2)) {
            int timeoutTicks = (int) (maxDuration * PeekConstants.DEFAULT_STATIC_TICKS);
            PeekMod.LOGGER.debug("Scheduled session timeout for {} after {} ticks ({} seconds)",
                ProfileCompat.getName(peeker.getGameProfile()), timeoutTicks, maxDuration);
            tickTaskManager.addTask(session.getId(), TASK_TYPE_SESSION_TIMEOUT, timeoutTicks,
                task -> stopPeekSession(peeker.getUuid(), false, getCurrentServer()));
        }
    }
    
    /**
     * Stops a peek session
     */
    public synchronized PeekConstants.Result<String> stopPeekSession(UUID peekerId, boolean voluntary) {
        return stopPeekSession(peekerId, voluntary, null);
    }
    
    /**
     * Stops a peek session with server context
     */
    public synchronized PeekConstants.Result<String> stopPeekSession(UUID peekerId, boolean voluntary, MinecraftServer server) {
        try {
            UUID sessionId = peekerToSession.get(peekerId);
            if (sessionId == null) {
                PeekMod.LOGGER.warn("Attempted to stop peek session for {} but no active session found", peekerId);
                return PeekConstants.Result.failure(ErrorCodes.NOT_PEEKING_ANYONE);
            }
            
            PeekSession session = activeSessions.get(sessionId);
            if (session == null) {
                PeekMod.LOGGER.warn("Session {} not found in active sessions for peeker {}", sessionId, peekerId);
                return PeekConstants.Result.failure(ErrorCodes.SESSION_NOT_FOUND);
            }
            
            PeekMod.LOGGER.debug("Stopping peek session for {} (voluntary: {}, duration: {}s)", 
                session.getPeekerName(), voluntary, session.getDurationSeconds());
            
            // Mark session as inactive
            session.markInactive();
            
            // Remove player from particle effects tracking
            ParticleEffectManager.removePlayer(peekerId, session.getTargetId());
            
            // Remove mappings
            activeSessions.remove(sessionId);
            peekerToSession.remove(peekerId);
            Set<UUID> targetSessions = targetToSession.get(session.getTargetId());
            if (targetSessions != null) {
                targetSessions.remove(sessionId);
                if (targetSessions.isEmpty()) {
                    targetToSession.remove(session.getTargetId());
                }
            }
            
            // Restore peeker's state
            // Use provided server or try to get current one
            if (server == null) {
                server = getCurrentServer();
            }
            if (server != null) {
                ServerPlayerEntity peeker = server.getPlayerManager().getPlayer(peekerId);
                if (peeker != null && ServerPlayerCompat.getServer(peeker) != null) {
                    PlayerState originalState = session.getOriginalPeekerState();
                    var registryManager = com.peek.utils.compat.PlayerCompat.getRegistryManager(peeker);
                    if (registryManager != null) {
                        originalState.restore(peeker, registryManager);
                    
                        // Clear saved state only after successful restoration
                        PlayerPeekData peekerData = PlayerDataApi.getCustomDataFor(peeker, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
                        if (peekerData != null) {
                            peekerData = peekerData.withSavedState(null);
                            PlayerDataApi.setCustomDataFor(peeker, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, peekerData);
                            // PlayerDataAPI will automatically save this data for online players
                        }
                        
                        // Send notification to peeker
                        Text endMessage = MessageBuilder.message("peek.message.ended_normal");
                        peeker.sendMessage(endMessage, false);
                    
                    // Play session end sound for peeker
                    SoundManager.playSessionEndSound(peeker);
                    
                    // Send notification to target (the player who was being peeked)
                    ServerPlayerEntity target = server.getPlayerManager().getPlayer(session.getTargetId());
                    if (target != null) {
                        Text targetMessage = MessageBuilder.message("peek.message.ended_by_target", session.getPeekerName());
                        target.sendMessage(targetMessage, false);
                        
                        // Play session end sound for target
                        SoundManager.playSessionEndSound(target);
                        
                        // Update command tree for target (remove who/cancel-player commands if no more sessions)
                        com.peek.utils.CommandUtils.updateCommandTree(target);
                    }
                    
                        // Update command tree for peeker (remove stop command)
                        com.peek.utils.CommandUtils.updateCommandTree(peeker);
                    }
                } else {
                    // Player is offline - save their original state using PlayerDataAPI's offline support
                    PlayerState originalState = session.getOriginalPeekerState();
                    if (originalState != null) {
                        try {
                            // Use PlayerDataAPI's UUID-based method for offline player data access
                            PlayerPeekData existingData = PlayerDataApi.getCustomDataFor(server, session.getPeekerId(), PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
                            PlayerPeekData updatedData = existingData != null ? 
                                existingData.withSavedState(originalState) :
                                new PlayerPeekData(false, false, Collections.emptyMap(), Collections.emptyMap(), originalState);
                            
                            PlayerDataApi.setCustomDataFor(server, session.getPeekerId(), PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, updatedData);
                            PeekMod.LOGGER.info("Player {} is offline, saved original state to PlayerDataAPI for crash recovery", session.getPeekerName());
                        } catch (Exception e) {
                            PeekMod.LOGGER.error("Failed to save state for offline player {} using PlayerDataAPI", session.getPeekerName(), e);
                        }
                    } else {
                        PeekMod.LOGGER.warn("No original state found for offline player {}", session.getPeekerName());
                    }
                }
            } else {
                PeekMod.LOGGER.error("No server instance available for state restoration");
            }
            
            // Record statistics
            long duration = session.getDurationSeconds();
            if (server != null) {
                ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).recordPeekSession(
                    server,
                    session.getPeekerId(), session.getPeekerName(),
                    session.getTargetId(), session.getTargetName(),
                    duration, session.hasCrossedDimension()
                );
            } else {
                PeekMod.LOGGER.warn("No server available to record statistics for session: {} -> {}", 
                    session.getPeekerName(), session.getTargetName());
            }
            
            LoggingHelper.logSessionWithDuration("Stopped", 
                session.getPeekerName(), session.getTargetName(), duration);
            
            return PeekConstants.Result.success(Text.translatable("peek.message.session_stopped_successfully").getString());
            
        } catch (Exception e) {
            PeekMod.LOGGER.error("Error stopping peek session", e);
            return PeekConstants.Result.failure(Text.translatable("peek.error.internal_error").getString());
        }
    }
    
    /**
     * Called every server tick to handle peek session updates and delayed teleportation
     */
    public void onServerTick() {
        // Always process delayed teleportation tasks every tick
        processDelayedTeleports();
        
        // Process unified tick tasks (session timeouts, etc.)
        tickTaskManager.processTick();
        
        // Process particle effects for active sessions every tick
        processParticleEffects();
        
        // Armor stand updates now handled in per-player tick for better performance
        
        // Update sessions every 20 ticks (1 second) or according to configuration
        int updateInterval = Math.max(1, (int) PeekConstants.SESSION_UPDATE_INTERVAL_TICKS);
        if (++tickCounter >= updateInterval) {
            tickCounter = 0;
            updateSessions();
        }
        
        // Cleanup every 60 seconds
        if (++cleanupTickCounter >= GameConstants.SESSION_CLEANUP_INTERVAL_TICKS) {
            cleanupTickCounter = 0;
            cleanupInactiveSessions();
            cleanupExpiredCircularPeekRecords();
            
            // Perform state consistency check every cleanup cycle
            MinecraftServer server = getCurrentServer();
            if (server != null) {
                com.peek.utils.StateConsistencyChecker.performConsistencyCheck(server);
            }
        }
    }
    
    /**
     * Process particle effects for all active sessions
     */
    private void processParticleEffects() {
        MinecraftServer server = getCurrentServer();
        if (server == null) {
            return;
        }
        
        try {
            // Process particles for each world that has players
            for (ServerWorld world : server.getWorlds()) {
                ParticleEffectManager.processParticleEffects(world);
            }
        } catch (Exception e) {
            PeekMod.LOGGER.error("Error processing particle effects", e);
        }
    }
    
    /**
     * Updates all active sessions (called at configured interval)
     */
    private void updateSessions() {
        if (activeSessions.isEmpty()) {
            return;
        }
        
        // Update each active session for distance and state checking
        for (PeekSession session : activeSessions.values()) {
            try {
                sessionUpdateHandler.updateSessionChecks(session, getCurrentServer(),
                        (peekerId, voluntary) -> {
                            PeekConstants.Result<String> result = stopPeekSession(peekerId, voluntary, getCurrentServer());
                            if (!result.isSuccess()) {
                                PeekMod.LOGGER.warn("Failed to stop session during update: {}", result.getError());
                            }
                        });
            } catch (Exception e) {
                PeekMod.LOGGER.error("Error updating peek session {}", session.getId(), e);
            }
        }
        
        // Armor stand positions are now updated every tick in onServerTick()
    }

    /**
     * Processes tick-based delayed teleportation tasks
     */
    private void processDelayedTeleports() {
        MinecraftServer server = getCurrentServer();
        if (server == null) {
            return;
        }
        
        List<TeleportationManager.DelayedTeleportTask> completedTasks = teleportationManager.processPendingTeleports();
        
        // Process completed teleportation tasks
        for (TeleportationManager.DelayedTeleportTask task : completedTasks) {
            try {
                // Re-verify players are still valid before teleporting
                ServerPlayerEntity peeker = server.getPlayerManager().getPlayer(task.peekerId);
                ServerPlayerEntity target = server.getPlayerManager().getPlayer(task.targetId);

                if (peeker == null || target == null) {
                    PeekMod.LOGGER.warn("Player became null during delayed teleport, ending session");
                    stopPeekSession(task.peekerId, false);
                    continue;
                }

                // Check if session is still active
                PeekSession currentSession = activeSessions.get(task.sessionId);
                if (currentSession == null || !currentSession.isActive()) {
                    PeekMod.LOGGER.debug("Session no longer active during delayed teleport");
                    continue;
                }

                PeekMod.LOGGER.debug("Executing delayed teleport to follow target {} to new dimension", task.targetName);
                teleportationManager.teleportPeekerToTarget(peeker, target);

                // Update session's world ID to reflect the successful dimension change
                UUID newWorldId = UUID.nameUUIDFromBytes(
                    ServerPlayerCompat.getWorld(target).getRegistryKey().getValue().toString().getBytes()
                );
                currentSession.updateTargetPosition(ServerPlayerCompat.getPos(target), newWorldId);
                
                PeekMod.LOGGER.debug("Updated session world ID after successful dimension follow");
                
                Text message = Text.translatable("peek.message.followed_dimension", task.targetName)
                    .formatted(Formatting.AQUA);
                peeker.sendMessage(message, false);
                
            } catch (Exception teleportError) {
                PeekMod.LOGGER.error("Failed to follow target to new dimension during delayed teleport, ending session", teleportError);
                
                ServerPlayerEntity errorPeeker = server.getPlayerManager().getPlayer(task.peekerId);
                if (errorPeeker != null) {
                    Text message = Text.translatable("peek.message.ended_teleport_failed");
                    errorPeeker.sendMessage(message, false);
                }
                
                stopPeekSession(task.peekerId, false);
            }
        }
    }
    
    // updateSessionChecks method is now handled by SessionUpdateHandler
    
    // handlePeekerDistanceExceeded method is now handled by TeleportationManager
    
    // teleportPeekerToTarget method is now handled by TeleportationManager
    
    // setServer() and getCurrentServer() are now inherited from BaseManager
    
    /**
     * Checks if a player is currently peeking someone
     */
    public boolean isPlayerPeeking(UUID playerId) {
        return peekerToSession.containsKey(playerId);
    }
    
    /**
     * Checks if a player is currently being peeked
     */
    public boolean isPlayerBeingPeeked(UUID playerId) {
        Set<UUID> sessions = targetToSession.get(playerId);
        return sessions != null && !sessions.isEmpty();
    }
    
    /**
     * Gets active session for a peeker
     */
    public PeekSession getSessionByPeeker(UUID peekerId) {
        UUID sessionId = peekerToSession.get(peekerId);
        return sessionId != null ? activeSessions.get(sessionId) : null;
    }
    
    /**
     * Gets the current target UUID that a peeker is peeking
     */
    public UUID getCurrentTarget(UUID peekerId) {
        PeekSession session = getSessionByPeeker(peekerId);
        return session != null ? session.getTargetId() : null;
    }
    
    /**
     * Gets first active session where player is target (deprecated, use getSessionsTargeting instead)
     */
    @Deprecated
    public PeekSession getSessionByTarget(UUID targetId) {
        Set<UUID> sessionIds = targetToSession.get(targetId);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            UUID firstSessionId = sessionIds.iterator().next();
            return activeSessions.get(firstSessionId);
        }
        return null;
    }
    
    /**
     * Gets all active sessions (for admin commands)
     */
    public Map<UUID, PeekSession> getActiveSessions() {
        return new ConcurrentHashMap<>(activeSessions);
    }
    
    /**
     * Gets all sessions where the specified player is being peeked (as target)
     */
    public List<PeekSession> getSessionsTargeting(UUID targetId) {
        List<PeekSession> targetingSessions = new ArrayList<>();
        Set<UUID> sessionIds = targetToSession.get(targetId);
        if (sessionIds != null) {
            for (UUID sessionId : sessionIds) {
                PeekSession session = activeSessions.get(sessionId);
                if (session != null) {
                    targetingSessions.add(session);
                }
            }
        }
        return targetingSessions;
    }
    
    /**
     * Forcefully stops all sessions involving a player (when they disconnect)
     */
    public void stopAllSessionsInvolving(UUID playerId) {
        stopAllSessionsInvolving(playerId, null);
    }
    
    /**
     * Forcefully stops all sessions involving a player with server context
     */
    public void stopAllSessionsInvolving(UUID playerId, MinecraftServer server) {
        // Stop as peeker
        if (isPlayerPeeking(playerId)) {
            stopPeekSession(playerId, false, server);
        }
        
        // Stop sessions where this player is targeted
        Set<UUID> sessionIds = targetToSession.get(playerId);
        if (sessionIds != null) {
            // Create a copy to avoid ConcurrentModificationException
            Set<UUID> sessionIdsCopy = new HashSet<>(sessionIds);
            for (UUID sessionId : sessionIdsCopy) {
                PeekSession session = activeSessions.get(sessionId);
                if (session != null) {
                    stopPeekSession(session.getPeekerId(), false, server);
                }
            }
        }
    }
    
    private boolean isPlayerStationary(ServerPlayerEntity player) {
        return player.getVelocity().lengthSquared() < 0.01;
    }
    
    /**
     * Stops a peek session without restoring the player's state (for peek switching)
     */
    private PeekConstants.Result<String> stopPeekSessionWithoutRestore(UUID peekerId) {
        try {
            UUID sessionId = peekerToSession.get(peekerId);
            if (sessionId == null) {
                return PeekConstants.Result.failure(ErrorCodes.NOT_PEEKING_ANYONE);
            }
            
            PeekSession session = activeSessions.get(sessionId);
            if (session == null) {
                return PeekConstants.Result.failure(ErrorCodes.SESSION_NOT_FOUND);
            }
            
            // Mark session as inactive
            session.markInactive();
            
            // Clean up particle effects for this session (for peek switching)
            ParticleEffectManager.removePlayer(peekerId, session.getTargetId());
            
            // Simple synchronized cleanup for data safety
            PeekConstants.Result<String> cleanupResult = cleanupSessionMappings(sessionId, peekerId, session.getTargetId());
            if (!cleanupResult.isSuccess()) {
                return cleanupResult;
            }
            
            // Send notification to target (the player who was being peeked) 
            MinecraftServer server = getCurrentServer();
            if (server != null) {
                ServerPlayerEntity target = server.getPlayerManager().getPlayer(session.getTargetId());
                if (target != null) {
                    Text targetMessage = Text.translatable("peek.message.peek_switched_target", session.getPeekerName());
                    target.sendMessage(targetMessage, false);
                }
            }
            
            // Record session in statistics
            if (server != null) {
                ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).recordPeekSession(
                    server,
                    session.getPeekerId(), session.getPeekerName(),
                    session.getTargetId(), session.getTargetName(),
                    session.getDurationSeconds(), session.hasCrossedDimension()
                );
            }
            
            // Update command visibility
            updateCommandVisibility(peekerId);
            updateCommandVisibility(session.getTargetId());
            
            return PeekConstants.Result.success(Text.translatable("peek.message.session_stopped_without_restore").getString());
            
        } catch (Exception e) {
            PeekMod.LOGGER.error("Error stopping peek session without restore", e);
            return PeekConstants.Result.failure(Text.translatable("peek.error.internal_error").getString());
        }
    }
    
    /**
     * Updates command visibility for a player by refreshing their command tree
     */
    private void updateCommandVisibility(UUID playerId) {
        MinecraftServer server = getCurrentServer();
        if (server != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                com.peek.utils.CommandUtils.updateCommandTree(player);
            }
        }
    }
    
    private void cleanupExpiredCircularPeekRecords() {
        long currentTime = System.currentTimeMillis();
        recentCircularPeeks.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue()) > GameConstants.CIRCULAR_PEEK_EXPIRY_MILLIS
        );
    }
    
    private void cleanupInactiveSessions() {
        activeSessions.entrySet().removeIf(entry -> {
            PeekSession session = entry.getValue();
            if (!session.isActive()) {
                
                // Clean up mappings
                peekerToSession.remove(session.getPeekerId());
                Set<UUID> targetSessions = targetToSession.get(session.getTargetId());
                if (targetSessions != null) {
                    targetSessions.remove(session.getId());
                    if (targetSessions.isEmpty()) {
                        targetToSession.remove(session.getTargetId());
                    }
                }
                return true;
            }
            return false;
        });
    }
    
    /**
     * Creates a rollback handler for session recovery
     */
    private SessionUtils.RollbackHandler createRollbackHandler(PlayerState existingOriginalState) {
        return new SessionUtils.RollbackHandler() {
            @Override
            public void handleSwitchingRollback(ServerPlayerEntity peeker, PeekSession sessionToRestore, UUID peekerId) {
                // Restore previous session for peek switching
                PeekMod.LOGGER.info("Rolling back peek switch, restoring previous session");
                activeSessions.put(sessionToRestore.getId(), sessionToRestore);
                peekerToSession.put(peekerId, sessionToRestore.getId());
                targetToSession.computeIfAbsent(sessionToRestore.getTargetId(), k -> ConcurrentHashMap.newKeySet())
                    .add(sessionToRestore.getId());
                
                // Restore previous spectator mode (don't restore to original state)
                peeker.changeGameMode(GameMode.SPECTATOR);
                
                
                // Ensure saved state in PlayerDataAPI is preserved (not cleared)
                PlayerPeekData rollbackPeekerData = PlayerDataApi.getCustomDataFor(peeker, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
                if (rollbackPeekerData == null || !rollbackPeekerData.hasSavedState()) {
                    rollbackPeekerData = rollbackPeekerData != null ? rollbackPeekerData.withSavedState(existingOriginalState) : 
                        new PlayerPeekData(false, false, Collections.emptyMap(), Collections.emptyMap(), existingOriginalState);
                    PlayerDataApi.setCustomDataFor(peeker, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, rollbackPeekerData);
                    PeekMod.LOGGER.info("Restored saved state to PlayerDataAPI during rollback");
                }
                
                PeekMod.LOGGER.info("Peek switch rollback completed - restored to previous peek session");
            }
            
            @Override
            public void handleNewSessionRollback(ServerPlayerEntity peeker) {
                // Full restoration for new session failure
                if (existingOriginalState != null) {
                    var registryManager = com.peek.utils.compat.PlayerCompat.getRegistryManager(peeker);
                    if (registryManager != null) {
                        existingOriginalState.restore(peeker, registryManager);
                    
                        // Clear saved state from PlayerDataAPI since we fully restored
                        PlayerPeekData restoredPeekerData = PlayerDataApi.getCustomDataFor(peeker, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
                        if (restoredPeekerData != null && restoredPeekerData.hasSavedState()) {
                            restoredPeekerData = restoredPeekerData.withSavedState(null);
                            PlayerDataApi.setCustomDataFor(peeker, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, restoredPeekerData);
                        }
                    }
                }
                
                PeekMod.LOGGER.info("New session rollback completed - restored to original state");
            }
        };
    }
    
    /**
     * Safely cleans up session mappings with proper synchronization
     */
    private synchronized PeekConstants.Result<String> cleanupSessionMappings(UUID sessionId, UUID peekerId, UUID targetId) {
        try {
            // Verify session still exists before cleanup
            if (!activeSessions.containsKey(sessionId)) {
                return PeekConstants.Result.failure("Session already removed");
            }
            
            // Remove from all mappings atomically
            activeSessions.remove(sessionId);
            peekerToSession.remove(peekerId);
            
            Set<UUID> targetSessions = targetToSession.get(targetId);
            if (targetSessions != null) {
                targetSessions.remove(sessionId);
                if (targetSessions.isEmpty()) {
                    targetToSession.remove(targetId);
                }
            }
            
            return PeekConstants.Result.success("Cleanup successful");
            
        } catch (Exception e) {
            PeekMod.LOGGER.error("Failed to cleanup session mappings", e);
            return PeekConstants.Result.failure(Text.translatable("peek.error.session_cleanup_failed").getString());
        }
    }

    /**
     * Notifies the previous target when a peeker switches to a new target
     */
    private void notifyPreviousTargetOfSwitch(PeekSession previousSession, ServerPlayerEntity newTarget) {
        MinecraftServer server = getCurrentServer();
        if (previousSession == null || server == null) {
            return;
        }
        
        ServerPlayerEntity previousTarget = server.getPlayerManager().getPlayer(previousSession.getTargetId());
        if (previousTarget != null) {
            // Only notify if this was the only peeker, or explain that peeker switched
            List<PeekSession> otherSessions = getSessionsTargeting(previousSession.getTargetId());
            if (otherSessions.size() <= 1) { // Only the current session which will be stopped
                Text message = Text.translatable("peek.message.peek_switch_away",
                    previousSession.getPeekerName(), ProfileCompat.getName(newTarget.getGameProfile()))
                    .formatted(Formatting.GRAY);
                previousTarget.sendMessage(message, false);
            }

            PeekMod.LOGGER.debug("Notified {} that {} switched peek to {}",
                ProfileCompat.getName(previousTarget.getGameProfile()),
                previousSession.getPeekerName(),
                ProfileCompat.getName(newTarget.getGameProfile()));
        }
    }

    @Override
    public void shutdown() {
        // Stop all active sessions
        for (UUID peekerId : peekerToSession.keySet()) {
            stopPeekSession(peekerId, false);
        }
        
        // Clear tick tasks
        tickTaskManager.clear();
        
        // Clear all particle effects
        ParticleEffectManager.shutdown();
        
        // Clear all data
        activeSessions.clear();
        peekerToSession.clear();
        targetToSession.clear();
        
        super.shutdown();
    }
    
    /**
     * Stop all active sessions and restore all players to their original states
     * Used during server shutdown to prevent data loss
     * @param server The server instance for player restoration
     * @return Number of players restored
     */
    public int stopAllSessionsAndRestore(MinecraftServer server) {
        synchronized (this) {
            int restoredCount = 0;
            
            try {
                PeekMod.LOGGER.info("Starting emergency session cleanup - {} active sessions", activeSessions.size());
                
                // Create a copy of session IDs to avoid modification during iteration
                List<UUID> sessionIds = new ArrayList<>(activeSessions.keySet());
                
                for (UUID sessionId : sessionIds) {
                    try {
                        PeekSession session = activeSessions.get(sessionId);
                        if (session == null) continue;
                        
                        UUID peekerId = session.getPeekerId();
                        UUID targetId = session.getTargetId();
                        
                        PeekMod.LOGGER.info("Emergency stopping session {} - peeker: {}, target: {}", 
                            sessionId, session.getPeekerName(), session.getTargetName());
                        
                        // Get peeker and restore their state
                        ServerPlayerEntity peeker = server.getPlayerManager().getPlayer(peekerId);
                        if (peeker != null) {
                            try {
                                // Restore peeker's original state
                                PlayerState originalState = session.getOriginalPeekerState();
                                if (originalState != null) {
                                    var registryManager = com.peek.utils.compat.PlayerCompat.getRegistryManager(server);
                                    if (registryManager != null) {
                                        originalState.restore(peeker, registryManager);
                                    
                                        // Clear saved state from persistent storage
                                        PlayerPeekData peekerData = PlayerDataApi.getCustomDataFor(peeker, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
                                        if (peekerData != null && peekerData.hasSavedState()) {
                                            peekerData = peekerData.withSavedState(null);
                                            PlayerDataApi.setCustomDataFor(peeker, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, peekerData);
                                            // PlayerDataAPI will automatically save this data for online players
                                        }
                                        
                                        restoredCount++;
                                        PeekMod.LOGGER.info("Restored player {} during emergency shutdown",
                                            ProfileCompat.getName(peeker.getGameProfile()));
                                    } else {
                                        PeekMod.LOGGER.warn("Cannot restore player {} - registry manager not available", ProfileCompat.getName(peeker.getGameProfile()));
                                    }
                                } else {
                                    PeekMod.LOGGER.warn("No original state found for peeker {} during emergency shutdown", 
                                        session.getPeekerName());
                                }
                            } catch (Exception e) {
                                PeekMod.LOGGER.error("Failed to restore peeker {} during emergency shutdown", 
                                    session.getPeekerName(), e);
                            }
                        } else {
                            PeekMod.LOGGER.warn("Peeker {} not found online during emergency shutdown", 
                                session.getPeekerName());
                        }
                        
                        // Clean up particle effects
                        ParticleEffectManager.removePlayer(peekerId, targetId);
                        
                    } catch (Exception e) {
                        PeekMod.LOGGER.error("Error during emergency session cleanup for session {}", sessionId, e);
                    }
                }
                
                // Clear all session mappings
                activeSessions.clear();
                peekerToSession.clear();
                targetToSession.clear();
                
                PeekMod.LOGGER.info("Emergency session cleanup completed - restored {} players", restoredCount);
                
            } catch (Exception e) {
                PeekMod.LOGGER.error("Critical error during emergency session cleanup", e);
            }
            
            return restoredCount;
        }
    }
}