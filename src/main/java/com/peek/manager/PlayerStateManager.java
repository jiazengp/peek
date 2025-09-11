package com.peek.manager;

import com.peek.PeekMod;
import com.peek.data.PeekDataStorage;
import com.peek.data.peek.PlayerPeekData;
import com.peek.data.peek.PlayerState;
import com.peek.manager.constants.PeekConstants;
import eu.pb4.playerdata.api.PlayerDataApi;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStateManager extends BaseManager {
    private final Map<UUID, PlayerState> tempStateCache = new ConcurrentHashMap<>();
    
    public PlayerStateManager() {
        // Public constructor for dependency injection
    }
    
    /**
     * Saves a player's state for later restoration
     * This is used both for normal peek operations and crash recovery
     */
    public PeekConstants.Result<PlayerState> savePlayerState(ServerPlayerEntity player, boolean persistent) {
        try {
            UUID playerId = player.getUuid();
            
            // Capture current state
            var registryManager = com.peek.utils.compat.PlayerCompat.getRegistryManager(player);
            if (registryManager == null) {
                PeekMod.LOGGER.error("Cannot capture player state for {} - server or registry manager not available", player.getGameProfile().getName());
                return PeekConstants.Result.failure("Server not available");
            }
            PlayerState state = PlayerState.capture(player, registryManager);
            
            if (persistent) {
                // Save to persistent storage via PlayerDataAPI for crash recovery
                PlayerPeekData playerData = com.peek.data.peek.PlayerPeekData.getOrCreate(player);
                
                playerData = playerData.withSavedState(state);
                PlayerDataApi.setCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, playerData);
                
                PeekMod.LOGGER.info("Saved persistent state for player {} to PlayerDataAPI", player.getGameProfile().getName());
            } else {
                // Save to temporary cache for quick access
                tempStateCache.put(playerId, state);
                PeekMod.LOGGER.debug("Saved temporary state for player {}", player.getGameProfile().getName());
            }
            
            return PeekConstants.Result.success(state);
            
        } catch (Exception e) {
            PeekMod.LOGGER.error("Error saving player state for {}", player.getGameProfile().getName(), e);
            return PeekConstants.Result.failure(Text.translatable("peek.message.failed_to_save_state").getString());
        }
    }

    /**
     * Checks if a player has a saved state
     */
    public boolean hasSavedState(UUID playerId, boolean checkPersistent) {
        if (checkPersistent) {
            PlayerPeekData data = getPersistentPlayerData(playerId);
            return data != null && data.hasSavedState();
        } else {
            return tempStateCache.containsKey(playerId);
        }
    }
    
    /**
     * Gets a saved state without removing it
     */
    public PlayerState getSavedState(UUID playerId, boolean fromPersistent) {
        if (fromPersistent) {
            PlayerPeekData data = getPersistentPlayerData(playerId);
            return data != null ? data.savedState() : null;
        } else {
            return tempStateCache.get(playerId);
        }
    }
    
    /**
     * Common method to get persistent player data with error handling
     */
    private PlayerPeekData getPersistentPlayerData(UUID playerId) {
        if (getCurrentServer() == null) {
            return null;
        }
        
        try {
            return PlayerDataApi.getCustomDataFor(getCurrentServer(), playerId, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
        } catch (Exception e) {
            PeekMod.LOGGER.warn("Error accessing persistent state for player {}", playerId, e);
            return null;
        }
    }
    
    /**
     * Clears a saved state without restoring it
     */
    public void clearSavedState(UUID playerId, boolean clearPersistent) {
        if (clearPersistent) {
            // This would need server access to clear from PlayerDataAPI
            PeekMod.LOGGER.debug("Clearing persistent state for player {}", playerId);
        } else {
            tempStateCache.remove(playerId);
            PeekMod.LOGGER.debug("Clearing temporary state for player {}", playerId);
        }
    }
    
    /**
     * Performs crash recovery for a player who was peeking when server shut down
     */
    public PeekConstants.Result<String> performCrashRecovery(ServerPlayerEntity player) {
        try {
            PeekMod.LOGGER.debug("Crash recovery: Getting player data for {}", player.getGameProfile().getName());
            PlayerPeekData playerData = com.peek.data.peek.PlayerPeekData.getOrCreate(player);
            
            PeekMod.LOGGER.debug("Crash recovery: Player data retrieved. HasSavedState: {}, SavedState: {}", 
                playerData.hasSavedState(), playerData.savedState() != null ? "Present" : "Null");
            
            if (playerData.hasSavedState()) {
                PlayerState savedState = playerData.savedState();
                
                PeekMod.LOGGER.debug("Found saved state for player {} - attempting crash recovery", player.getGameProfile().getName());
                if (savedState != null) {
                    PeekMod.LOGGER.debug("Saved state details: pos={}, fire={}, air={}, gamemode={}",
                        savedState.position(), savedState.fireTicks(), savedState.air(), savedState.gameMode());
                }

                // Validate the state before restoration
                if (!isStateValid(savedState)) {
                    PeekMod.LOGGER.warn("Saved state for player {} is invalid - clearing it", player.getGameProfile().getName());
                    // Clear the invalid saved state
                    playerData = playerData.withSavedState(null);
                    PlayerDataApi.setCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, playerData);
                    // PlayerDataAPI will automatically save this data for online players
                    return PeekConstants.Result.failure("Saved state was invalid and has been cleared");
                }
                
                // Attempt to restore the state
                try {
                    restoreState(player, savedState);
                    PeekMod.LOGGER.debug("State restoration completed successfully for player {}", player.getGameProfile().getName());
                    
                    // Only clear the saved state after successful restoration
                    playerData = playerData.withSavedState(null);
                    PlayerDataApi.setCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, playerData);
                    // PlayerDataAPI will automatically save this data for online players
                    PeekMod.LOGGER.debug("Cleared saved state after successful recovery for player {}", player.getGameProfile().getName());
                    
                    PeekMod.LOGGER.debug("Successfully performed crash recovery for player {}", player.getGameProfile().getName());
                    return PeekConstants.Result.success(Text.translatable("peek.message.crash_recovery_completed").getString());
                    
                } catch (Exception restoreException) {
                    PeekMod.LOGGER.error("Failed to restore state for player {} - keeping saved state for retry", 
                        player.getGameProfile().getName(), restoreException);
                    
                    // DO NOT clear the saved state - keep it for potential retry
                    return PeekConstants.Result.failure("State restoration failed: " + restoreException.getMessage() + 
                        " (saved state preserved for retry)");
                }
            }
            
            PeekMod.LOGGER.debug("No saved state found for player {} - no crash recovery needed", player.getGameProfile().getName());
            return PeekConstants.Result.success(Text.translatable("peek.message.no_crash_recovery_needed").getString());
            
        } catch (Exception e) {
            PeekMod.LOGGER.error("Error during crash recovery for {}", player.getGameProfile().getName(), e);
            
            // Try to clear the problematic saved state
            try {
                PlayerPeekData playerData = com.peek.data.peek.PlayerPeekData.getOrCreate(player);
                if (playerData.hasSavedState()) {
                    playerData = playerData.withSavedState(null);
                    PlayerDataApi.setCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, playerData);
                    PeekMod.LOGGER.info("Cleared problematic saved state for player {}", player.getGameProfile().getName());
                }
            } catch (Exception clearException) {
                PeekMod.LOGGER.error("Failed to clear problematic saved state for {}", player.getGameProfile().getName(), clearException);
            }
            
            return PeekConstants.Result.failure(Text.translatable("peek.message.crash_recovery_failed").getString());
        }
    }

    /**
     * Creates a backup of current state before making changes
     */
    public void createStateBackup(ServerPlayerEntity player) {
        savePlayerState(player, false); // Save to temporary cache as backup
    }
    
    /**
     * Validates that a player state is reasonable and safe to restore
     */
    public boolean isStateValid(PlayerState state) {
        try {
            // Basic validation checks
            if (state.position() == null) return false;
            if (state.gameMode() == null) return false;
            if (state.fireTicks() < 0 || state.fireTicks() > 32767) return false; // Reasonable fire ticks bounds
            if (state.air() < 0 || state.air() > 300) return false; // Reasonable air bounds (300 is max)
            if (state.vehicleBubbleTime() < 0) return false; // Vehicle bubble time should not be negative
            
            return true;
        } catch (Exception e) {
            PeekMod.LOGGER.warn("State validation failed", e);
            return false;
        }
    }

    // Private helper methods
    private void restoreState(ServerPlayerEntity player, PlayerState state) {
        // Validate state before restoration
        if (!isStateValid(state)) {
            throw new IllegalArgumentException(Text.translatable("peek.message.invalid_state").getString());
        }
        
        // Restore using the PlayerState's restore method (this handles position restoration internally)
        var registryManager = com.peek.utils.compat.PlayerCompat.getRegistryManager(player);
        if (registryManager == null) {
            throw new IllegalStateException("Cannot restore player state - server or registry manager not available");
        }
        state.restore(player, registryManager);
        
        // Note: Position restoration is handled by PlayerState.restore() to avoid recursion
        // If additional position safety checks are needed, they should be done before calling restore()
    }
}