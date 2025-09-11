package com.peek.utils;

import com.mojang.brigadier.context.CommandContext;
import com.peek.data.PeekDataStorage;
import com.peek.data.peek.PlayerPeekData;
import com.peek.manager.ManagerRegistry;
import com.peek.manager.PeekRequestManager;
import com.peek.manager.PeekSessionManager;
import com.peek.utils.compat.TextEventCompat;
import eu.pb4.playerdata.api.PlayerDataApi;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract base class for managing player lists (blacklist/whitelist)
 * Provides common functionality that can be reused between different list types
 */
public abstract class AbstractPlayerListManager {
    
    /**
     * Gets the name of this list type for display purposes
     * @return "blacklist" or "whitelist"
     */
    protected abstract String getListType();
    
    /**
     * Gets the capitalized name of this list type for messages
     * @return "Blacklist" or "Whitelist"
     */
    protected abstract String getListTypeCapitalized();
    
    /**
     * Gets the list from player data
     * @param data Player data
     * @return The list (blacklist or whitelist)
     */
    protected abstract Map<UUID, Long> getList(PlayerPeekData data);
    
    /**
     * Updates the list in player data
     * @param data Current player data
     * @param newList Updated list
     * @return Updated player data
     */
    protected abstract PlayerPeekData updateList(PlayerPeekData data, Map<UUID, Long> newList);
    
    /**
     * Adds a player to this list with mutual exclusion handling
     * @param data Current player data
     * @param playerId Player to add
     * @return Updated player data
     */
    protected abstract PlayerPeekData addWithMutualExclusion(PlayerPeekData data, UUID playerId);
    
    /**
     * Removes a player from this list
     * @param data Current player data
     * @param playerId Player to remove
     * @return Updated player data
     */
    protected abstract PlayerPeekData removeFromList(PlayerPeekData data, UUID playerId);
    
    /**
     * Handles the add command for this list type
     */
    public int handleAddCommand(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            ServerPlayerEntity target = CommandUtils.getPlayerArgument(context, "player");
            if (!ValidationUtils.validatePlayerNotNull(target, player)) return 0;

            PlayerPeekData data = getOrCreatePlayerData(player);
            if (!validateListOperation(data, target.getUuid(), target.getDisplayName(), player, true)) {
                return 0;
            }
            
            // Check if this would cause removal from opposite list
            boolean wouldCauseRemoval = PlayerListMutualExclusion.wouldCauseRemoval(
                data, target.getUuid(), getListType().equals("whitelist"));
            
            data = addWithMutualExclusion(data, target.getUuid());
            PlayerDataApi.setCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, data);
            
            // Handle active sessions and pending requests (important for blacklist)
            if (getListType().equals("blacklist")) {
                handleBlacklistSessionsAndRequests(player, target);
            }
            
            // Send confirmation message
            player.sendMessage(Text.translatable("peek." + getListType() + ".added", 
                target.getDisplayName()), false);
            
            // Send mutual exclusion notification if applicable
            if (wouldCauseRemoval) {
                String oppositeList = PlayerListMutualExclusion.getOppositeListName(getListType().equals("whitelist"));
                player.sendMessage(Text.translatable("peek." + getListType() + ".moved_from_" + oppositeList, 
                    target.getDisplayName()).formatted(Formatting.GRAY), false);
            }
            
            return 1;
        });
    }
    
    /**
     * Handles the remove command for this list type
     */
    public int handleRemoveCommand(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            PlayerPeekData data = getOrCreatePlayerData(player);
            
            // Check if list is empty
            if (getList(data).isEmpty()) {
                player.sendMessage(Text.translatable("peek." + getListType() + ".empty"), false);
                return 0;
            }
            
            ServerPlayerEntity target = CommandUtils.getPlayerArgument(context, "player");
            if (!ValidationUtils.validatePlayerNotNull(target, player)) return 0;

            if (!validateListOperation(data, target.getUuid(), target.getDisplayName(), player, false)) {
                return 0;
            }
            
            data = removeFromList(data, target.getUuid());
            PlayerDataApi.setCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, data);
            player.sendMessage(Text.translatable("peek." + getListType() + ".removed", 
                target.getDisplayName()), false);
            return 1;
        });
    }
    
    /**
     * Handles the list command for this list type
     */
    public int handleListCommand(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            PlayerPeekData data = getOrCreatePlayerData(player);
            Map<UUID, Long> list = getList(data);
            
            if (list.isEmpty()) {
                player.sendMessage(Text.translatable("peek." + getListType() + ".empty"), false);
                return 1;
            }
            
            MutableText message = Text.translatable("peek." + getListType() + ".header");
            
            // Add list entries with resolved player names, timestamps, and remove buttons
            int count = 0;
            for (var entry : list.entrySet()) {
                UUID uuid = entry.getKey();
                Long timestamp = entry.getValue();
                count++;
                
                ServerPlayerEntity listPlayer = player.getServer().getPlayerManager().getPlayer(uuid);
                String playerName;
                
                if (listPlayer != null) {
                    // Player is online, use current name
                    playerName = listPlayer.getName().getString();
                } else {
                    // Player is offline, try to get name from player cache
                    var profile = player.getServer().getUserCache().getByUuid(uuid);
                    playerName = profile.isPresent() ? profile.get().getName() : "Unknown Player";
                }
                
                // Format timestamp
                String timeString = "Unknown time";
                if (timestamp != null) {
                    LocalDateTime dateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    timeString = dateTime.format(formatter);
                }
                
                // Create remove button
                MutableText removeButton = Text.translatable("peek." + getListType() + ".remove_button")
                    .styled(style -> style
                        .withClickEvent(TextEventCompat.runCommand("/peek settings " + getListType() + " remove " + playerName))
                        .withHoverEvent(TextEventCompat.showText(Text.translatable("peek." + getListType() + ".remove_button_tip", playerName))));
                
                // Add player entry to message with timestamp and remove button
                message.append(Text.literal("\n§7" + count + ". §f" + playerName + " §8(" + timeString + ") "))
                       .append(removeButton);
            }
            
            player.sendMessage(message, false);
            return 1;
        });
    }
    
    /**
     * Validates list operations (add/remove)
     */
    protected boolean validateListOperation(PlayerPeekData data, UUID targetId, 
                                          net.minecraft.text.Text targetName, ServerPlayerEntity executor, boolean isAddOperation) {
        Map<UUID, Long> list = getList(data);
        boolean inList = list.containsKey(targetId);
        
        if (isAddOperation && inList) {
            executor.sendMessage(Text.translatable("peek." + getListType() + ".already_exists", targetName)
                .formatted(Formatting.YELLOW), false);
            return false;
        } else if (!isAddOperation && !inList) {
            executor.sendMessage(Text.translatable("peek." + getListType() + ".not_exists", targetName)
                .formatted(Formatting.YELLOW), false);
            return false;
        }
        
        return true;
    }
    
    /**
     * Helper method to get or create player data
     */
    protected PlayerPeekData getOrCreatePlayerData(ServerPlayerEntity player) {
        return PlayerPeekData.getOrCreate(player);
    }
    
    /**
     * Handles session and request management when adding to blacklist
     */
    private void handleBlacklistSessionsAndRequests(ServerPlayerEntity player, ServerPlayerEntity target) {
        PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
        PeekRequestManager requestManager = ManagerRegistry.getInstance().getManager(PeekRequestManager.class);
        
        // 1. If the blacklisted player is currently peeking the blacklister, stop their peek session
        if (sessionManager.isPlayerPeeking(target.getUuid())) {
            var targetSession = sessionManager.getSessionByPeeker(target.getUuid());
            if (targetSession != null && targetSession.getTargetId().equals(player.getUuid())) {
                // The blacklisted player is peeking the blacklister, stop the session
                sessionManager.stopPeekSession(target.getUuid(), false, player.getServer());
                
                // Notify both players
                target.sendMessage(Text.translatable("peek.message.ended_blacklisted"), false);
                player.sendMessage(Text.translatable("peek.message.blacklist_stopped_peek", target.getDisplayName()), false);
            }
        }
        
        // 2. Cancel any pending requests between the players
        requestManager.cancelRequestsBetween(player.getUuid(), target.getUuid());
    }
}