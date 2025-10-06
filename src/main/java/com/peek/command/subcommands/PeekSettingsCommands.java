package com.peek.command.subcommands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.peek.PeekMod;
import com.peek.data.PeekDataStorage;
import com.peek.data.peek.PeekSession;
import com.peek.data.peek.PlayerPeekData;
import com.peek.manager.ManagerRegistry;
import com.peek.manager.PeekSessionManager;
import com.peek.manager.constants.PeekConstants;
import com.peek.utils.BlacklistCommandBuilder;
import com.peek.utils.CommandUtils;
import com.peek.utils.WhitelistCommandBuilder;
import com.peek.utils.compat.ProfileCompat;
import com.peek.utils.compat.ServerPlayerCompat;
import com.peek.utils.permissions.PermissionChecker;
import com.peek.utils.permissions.Permissions;
import eu.pb4.playerdata.api.PlayerDataApi;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles peek settings related commands: settings, private, auto-accept, blacklist, whitelist
 */
public class PeekSettingsCommands {
    
    // Command builders using the new abstraction system
    private static final BlacklistCommandBuilder blacklistBuilder = new BlacklistCommandBuilder();
    private static final WhitelistCommandBuilder whitelistBuilder = new WhitelistCommandBuilder();
    
    public static LiteralArgumentBuilder<ServerCommandSource> createSettingsCommand() {
        return CommandManager.literal("settings")
                .requires(source -> PermissionChecker.hasPermission(source, Permissions.Command.SETTINGS, 0))
                .then(createPrivateSettingCommand())
                .then(createAutoAcceptSettingCommand())
                .then(createBlacklistSettingCommand())
                .then(createWhitelistSettingCommand());
    }
    
    private static LiteralArgumentBuilder<ServerCommandSource> createPrivateSettingCommand() {
        return CommandManager.literal("private")
                .requires(source -> PermissionChecker.hasPermission(source, Permissions.Command.SETTINGS_PRIVATE, 0))
                .executes(PeekSettingsCommands::togglePrivateMode)
                .then(CommandManager.literal("on").executes(ctx -> setPrivateMode(ctx, true)))
                .then(CommandManager.literal("off").executes(ctx -> setPrivateMode(ctx, false)));
    }
    
    private static LiteralArgumentBuilder<ServerCommandSource> createAutoAcceptSettingCommand() {
        return CommandManager.literal("auto-accept")
                .requires(source -> PermissionChecker.hasPermission(source, Permissions.Command.SETTINGS_AUTO_ACCEPT, 0))
                .executes(PeekSettingsCommands::toggleAutoAccept)
                .then(CommandManager.literal("on").executes(ctx -> setAutoAccept(ctx, true)))
                .then(CommandManager.literal("off").executes(ctx -> setAutoAccept(ctx, false)));
    }
    
    private static LiteralArgumentBuilder<ServerCommandSource> createBlacklistSettingCommand() {
        return blacklistBuilder.createCommand();
    }
    
    private static LiteralArgumentBuilder<ServerCommandSource> createWhitelistSettingCommand() {
        return whitelistBuilder.createCommand();
    }
    
    private static int togglePrivateMode(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            PlayerPeekData data = getOrCreatePlayerData(player);
            boolean currentState = data.privateMode();
            boolean newState = !currentState;
            
            // Update the data
            data = data.withPrivateMode(newState);
            PlayerDataApi.setCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, data);
            
            // Send simple success confirmation for toggle operations
            String key = newState ? "peek.settings.private_toggled_on" : "peek.settings.private_toggled_off";
            player.sendMessage(Text.translatable(key), false);
            
            // Notify if auto-accept was disabled
            if (newState && data.autoAccept()) {
                player.sendMessage(Text.translatable("peek.settings.auto_accept_disabled_by_private"), false);
            }
            
            // If enabling private mode, clear all sessions targeting this player
            if (newState) {
                clearAllSessionsTargetingPlayer(player);
            }
            
            return 1;
        });
    }
    
    private static int setPrivateMode(CommandContext<ServerCommandSource> context, boolean enabled) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            PlayerPeekData data = getOrCreatePlayerData(player);

            data = data.withPrivateMode(enabled);
            PlayerDataApi.setCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, data);
            
            String key = enabled ? "peek.settings.private_enabled" : "peek.settings.private_disabled";
            player.sendMessage(Text.translatable(key), false);
            
            // Notify if auto-accept was disabled
            if (enabled && data.autoAccept()) {
                player.sendMessage(Text.translatable("peek.settings.auto_accept_disabled_by_private"), false);
            }
            
            // If enabling private mode, clear all sessions targeting this player
            // Always clear when explicitly setting to 'on', even if already private (force cleanup)
            if (enabled) {
                clearAllSessionsTargetingPlayer(player);
            }
            
            return 1;
        });
    }
    
    /**
     * Clears all active peek sessions targeting the specified player (when they enable private mode)
     */
    private static void clearAllSessionsTargetingPlayer(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
        
        // Get all sessions targeting this player
        List<PeekSession> targetingSessions = sessionManager.getSessionsTargeting(playerId);
        
        if (targetingSessions.isEmpty()) {
            PeekMod.LOGGER.debug("No sessions targeting {} to clear for private mode", ProfileCompat.getName(player.getGameProfile()));
            return; // No sessions to clear
        }

        PeekMod.LOGGER.debug("Clearing {} peek sessions targeting {} due to private mode activation",
            targetingSessions.size(), ProfileCompat.getName(player.getGameProfile()));
        
        // Create a copy to avoid concurrent modification issues
        List<UUID> peekerIds = new ArrayList<>();
        for (PeekSession session : targetingSessions) {
            peekerIds.add(session.getPeekerId());
            PeekMod.LOGGER.debug("Found session {} - {} peeking {}", 
                session.getId(), session.getPeekerName(), session.getTargetName());
        }
        
        int successfullyCleared = 0;
        
        // Stop each targeting session
        for (UUID peekerId : peekerIds) {
            try {
                // Stop the session
                PeekConstants.Result<String> result = sessionManager.stopPeekSession(peekerId, false, ServerPlayerCompat.getServer(player));

                if (result.isSuccess()) {
                    successfullyCleared++;
                    PeekMod.LOGGER.debug("Successfully stopped session for peeker {}", peekerId);

                    // Notify the peeker about why their session was ended
                    if (ServerPlayerCompat.getServer(player) == null) {
                        return;
                    }

                    ServerPlayerEntity peeker = ServerPlayerCompat.getServer(player).getPlayerManager().getPlayer(peekerId);
                    if (peeker != null) {
                        Text message = Text.translatable("peek.message.ended_private_mode", player.getDisplayName());
                        peeker.sendMessage(message, false);
                    }
                } else {
                    PeekMod.LOGGER.warn("Failed to stop session for peeker {}: {}", peekerId, result.getError());
                }
            } catch (Exception e) {
                PeekMod.LOGGER.error("Exception while stopping session for peeker {}: {}", peekerId, e.getMessage(), e);
            }
        }
        
        // Notify the player about how many sessions were cleared
        if (successfullyCleared > 0) {
            Text message = Text.translatable("peek.message.private_mode_cleared_sessions", successfullyCleared)
                .formatted(Formatting.YELLOW);
            player.sendMessage(message, false);
            
            PeekMod.LOGGER.debug("Successfully cleared {} out of {} sessions targeting {}",
                successfullyCleared, targetingSessions.size(), ProfileCompat.getName(player.getGameProfile()));
        } else {
            PeekMod.LOGGER.warn("Failed to clear any sessions targeting {} (found {} sessions)",
                ProfileCompat.getName(player.getGameProfile()), targetingSessions.size());
        }
    }
    
    private static int toggleAutoAccept(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            PlayerPeekData data = getOrCreatePlayerData(player);
            boolean currentState = data.autoAccept();
            boolean newState = !currentState;
            
            // Update the data
            data = data.withAutoAccept(newState);
            PlayerDataApi.setCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, data);
            
            // Send simple success confirmation for toggle operations
            String key = newState ? "peek.settings.auto_accept_toggled_on" : "peek.settings.auto_accept_toggled_off";
            player.sendMessage(Text.translatable(key), false);
            
            // Notify if private mode was disabled
            if (newState && data.privateMode()) {
                player.sendMessage(Text.translatable("peek.settings.private_disabled_by_auto_accept"), false);
            }
            
            if (newState) {
                player.sendMessage(Text.translatable("peek.settings.auto_accept_info", 
                    com.peek.config.ModConfigManager.getAutoAcceptDelaySeconds()), false);
            }
            
            return 1;
        });
    }
    
    private static int setAutoAccept(CommandContext<ServerCommandSource> context, boolean enabled) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            PlayerPeekData data = getOrCreatePlayerData(player);
            data = data.withAutoAccept(enabled);
            PlayerDataApi.setCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, data);
            
            String key = enabled ? "peek.settings.auto_accept_enabled" : "peek.settings.auto_accept_disabled";
            player.sendMessage(Text.translatable(key), false);
            
            // Notify if private mode was disabled
            if (enabled && data.privateMode()) {
                player.sendMessage(Text.translatable("peek.settings.private_disabled_by_auto_accept"), false);
            }
            
            if (enabled) {
                player.sendMessage(Text.translatable("peek.settings.auto_accept_info", 
                    com.peek.config.ModConfigManager.getAutoAcceptDelaySeconds()), false);
            }
            
            return 1;
        });
    }
    
    
    // Helper method
    private static PlayerPeekData getOrCreatePlayerData(ServerPlayerEntity player) {
        return PlayerPeekData.getOrCreate(player);
    }
}