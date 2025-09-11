package com.peek;

import com.peek.command.PeekCommand;
import com.peek.command.PeekAdminCommand;
import com.peek.config.ModConfigManager;
import com.peek.data.PeekDataStorage;
import com.peek.manager.*;
import com.peek.placeholders.Placeholders;
import com.peek.utils.*;
import com.peek.manager.constants.PeekConstants;
import com.peek.utils.ParticleEffectManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.pb4.playerdata.api.PlayerDataApi;

public class PeekMod implements ModInitializer {
	public static final String MOD_ID = "peekmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModConfigManager.loadConfig();
		ModMetadataHolder.load();

		// Register PlayerDataAPI storage
		PlayerDataApi.register(PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> {
					PeekCommand.register(dispatcher);
					PeekAdminCommand.register(dispatcher);
				}
		);

		Placeholders.registerPlaceholders();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			try {
				// Initialize ManagerRegistry and trigger manager creation
				ManagerRegistry registry = ManagerRegistry.getInstance();
				
				// Trigger manager initialization by accessing them
				registry.getManager(PlayerStateManager.class);
				registry.getManager(PeekRequestManager.class);
				registry.getManager(InviteManager.class);
				registry.getManager(PeekSessionManager.class);
				registry.getManager(PeekStatisticsManager.class);
				
				// Set server references for all managers
				registry.setServer(server);
				
				LOGGER.debug("Peek mod initialized successfully with ManagerRegistry");
			} catch (Exception e) {
				LOGGER.error("Failed to initialize peek mod", e);
			}
		});

		// Register server tick event for session updates, delayed teleportation, and request handling
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			try {
				ManagerRegistry registry = ManagerRegistry.getInstance();
				registry.getManager(PeekSessionManager.class).onServerTick();
				registry.getManager(PeekRequestManager.class).onServerTick();
				
				// Process delayed tasks
				TickTaskManager.getInstance().processTick();
			} catch (Exception e) {
				LOGGER.error("Error during server tick processing", e);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			try {
				LOGGER.info("Server stopping - cleaning up all active sessions and restoring player states");
				
				ManagerRegistry registry = ManagerRegistry.getInstance();
				
				// First, stop all active sessions and restore players to original states
				try {
					PeekSessionManager sessionManager = registry.getManager(PeekSessionManager.class);
					int restoredCount = sessionManager.stopAllSessionsAndRestore(server);
					LOGGER.info("Restored {} players from active peek sessions during shutdown", restoredCount);
				} catch (Exception e) {
					LOGGER.error("Error stopping sessions during shutdown", e);
				}
				
				// PlayerDataAPI will automatically save any remaining data during graceful shutdown
				// No manual emergency save needed - session cleanup should have handled everything
				
				// Shutdown managers gracefully
				registry.getManager(PeekRequestManager.class).shutdown();
				registry.getManager(PeekSessionManager.class).shutdown();
				registry.getManager(PeekStatisticsManager.class).saveAndShutdown();
				
				LOGGER.info("Peek mod shutdown successfully with session cleanup");
			} catch (Exception e) {
				LOGGER.error("Failed to shutdown peek mod", e);
			}
		});

		// Handle player connections for crash recovery
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			LOGGER.debug("Player {} joined server - scheduling crash recovery check", player.getGameProfile().getName());
			
			// Schedule crash recovery check with proper delay using TickTaskManager
			try {
				ManagerRegistry registry = ManagerRegistry.getInstance();
				
				// Use TickTaskManager for proper delayed execution
				TickTaskManager.scheduleDelayedTask(() -> {
					try {
						// Check if player has saved state from previous session (crash recovery)
						PeekConstants.Result<String> result = registry.getManager(PlayerStateManager.class).performCrashRecovery(player);
						if (result.isSuccess()) {
							// Only log if actual recovery happened (not "no recovery needed")
							if (result.getValue().contains("completed")) {
								LOGGER.info("Crash recovery completed for player {}", player.getGameProfile().getName());
							} else {
								LOGGER.debug("Crash recovery check for player {}: {}", player.getGameProfile().getName(), result.getValue());
							}
						} else {
							LOGGER.warn("Crash recovery failed for player {}: {}", player.getGameProfile().getName(), result.getError());
						}
					} catch (Exception e) {
						LOGGER.error("Error during crash recovery check for {}", player.getGameProfile().getName(), e);
					}
				}, 3); // 3-tick delay to ensure player is fully loaded
			} catch (Exception e) {
				LOGGER.error("Error scheduling crash recovery for {}", player.getGameProfile().getName(), e);
			}
		});

		// Handle player disconnections
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			try {
				// Clean up particle effects for this player
				ParticleEffectManager.cleanupPlayerParticles(player.getUuid());
				
				// Stop any active sessions involving this player, passing server for offline state saving
				ManagerRegistry.getInstance().getManager(PeekSessionManager.class).stopAllSessionsInvolving(player.getUuid(), server);
				LOGGER.debug("Cleaned up sessions and particle effects for disconnecting player {}", player.getGameProfile().getName());
			} catch (Exception e) {
				LOGGER.error("Error cleaning up sessions for {}", player.getGameProfile().getName(), e);
			}
		});

		// Handle player death events
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof ServerPlayerEntity player) {
				try {
					// Clean up particle effects for this player
					ParticleEffectManager.cleanupPlayerParticles(player.getUuid());
					
					// Stop any active sessions involving this player when they die
					ManagerRegistry.getInstance().getManager(PeekSessionManager.class).stopAllSessionsInvolving(player.getUuid());
					LOGGER.debug("Cleaned up sessions and particle effects for dead player {}", player.getGameProfile().getName());
				} catch (Exception e) {
					LOGGER.error("Error cleaning up sessions for dead player {}", player.getGameProfile().getName(), e);
				}
			}
		});

		LOGGER.info("Peek Mod initialized successfully!");
	}
}