package com.peek.utils;

import com.mojang.brigadier.context.CommandContext;
import com.peek.data.PeekDataStorage;
import com.peek.data.peek.PlayerPeekData;
import com.peek.manager.ManagerRegistry;
import com.peek.manager.PeekRequestManager;
import com.peek.manager.PeekSessionManager;
import com.peek.utils.compat.ProfileCompat;
import com.peek.utils.compat.ServerPlayerCompat;
import com.peek.utils.compat.TextEventCompat;
import com.peek.utils.compat.UserCacheCompat;
import eu.pb4.playerdata.api.PlayerDataApi;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

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
    public int handleAddCommand(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            ServerPlayer target = CommandUtils.getPlayerArgument(context, "player");
            if (!ValidationUtils.validatePlayerNotNull(target, player)) return 0;

            PlayerPeekData data = PlayerPeekData.getOrCreate(player);
            if (!validateListOperation(data, target.getUUID(), target.getDisplayName(), player, true)) {
                return 0;
            }
            
            // Check if this would cause removal from opposite list
            boolean wouldCauseRemoval = PlayerListMutualExclusion.wouldCauseRemoval(
                data, target.getUUID(), getListType().equals("whitelist"));
            
            data = addWithMutualExclusion(data, target.getUUID());
            PlayerDataApi.setCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, data);
            
            // Handle active sessions and pending requests (important for blacklist)
            if (getListType().equals("blacklist")) {
                handleBlacklistSessionsAndRequests(player, target);
            }
            
            // Send confirmation message
            player.sendSystemMessage(Component.translatable("peek." + getListType() + ".added", 
                target.getDisplayName()), false);
            
            // Send mutual exclusion notification if applicable
            if (wouldCauseRemoval) {
                String oppositeList = PlayerListMutualExclusion.getOppositeListName(getListType().equals("whitelist"));
                player.sendSystemMessage(Component.translatable("peek." + getListType() + ".moved_from_" + oppositeList, 
                    target.getDisplayName()).withStyle(ChatFormatting.GRAY), false);
            }
            
            return 1;
        });
    }
    
    /**
     * Handles the remove command for this list type
     */
    public int handleRemoveCommand(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            PlayerPeekData data = PlayerPeekData.getOrCreate(player);
            
            // Check if list is empty
            if (getList(data).isEmpty()) {
                player.sendSystemMessage(Component.translatable("peek." + getListType() + ".empty"), false);
                return 0;
            }
            
            ServerPlayer target = CommandUtils.getPlayerArgument(context, "player");
            if (!ValidationUtils.validatePlayerNotNull(target, player)) return 0;

            if (!validateListOperation(data, target.getUUID(), target.getDisplayName(), player, false)) {
                return 0;
            }
            
            data = removeFromList(data, target.getUUID());
            PlayerDataApi.setCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE, data);
            player.sendSystemMessage(Component.translatable("peek." + getListType() + ".removed", 
                target.getDisplayName()), false);
            return 1;
        });
    }
    
    /**
     * Handles the list command for this list type
     */
    public int handleListCommand(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            PlayerPeekData data = PlayerPeekData.getOrCreate(player);
            Map<UUID, Long> list = getList(data);
            
            if (list.isEmpty()) {
                player.sendSystemMessage(Component.translatable("peek." + getListType() + ".empty"), false);
                return 1;
            }
            
            MutableComponent message = Component.translatable("peek." + getListType() + ".header");
            
            // Add list entries with resolved player names, timestamps, and remove buttons
            int count = 0;
            for (var entry : list.entrySet()) {
                UUID uuid = entry.getKey();
                Long timestamp = entry.getValue();
                count++;
                
                ServerPlayer listPlayer = ServerPlayerCompat.getServer(player).getPlayerList().getPlayer(uuid);
                String playerName;

                if (listPlayer != null) {
                    // Player is online, use current name
                    playerName = listPlayer.getName().getString();
                } else {
                    // Player is offline, try to get name from player cache
                    playerName = UserCacheCompat.getNameByUuid(ServerPlayerCompat.getServer(player), uuid).orElse("Unknown Player");
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
                MutableComponent removeButton = Component.translatable("peek." + getListType() + ".remove_button")
                    .withStyle(style -> style
                        .withClickEvent(TextEventCompat.runCommand("/peek settings " + getListType() + " remove " + playerName))
                        .withHoverEvent(TextEventCompat.showText(Component.translatable("peek." + getListType() + ".remove_button_tip", playerName))));
                
                // Add player entry to message with timestamp and remove button
                message.append(Component.literal("\n§7" + count + ". §f" + playerName + " §8(" + timeString + ") "))
                       .append(removeButton);
            }
            
            player.sendSystemMessage(message, false);
            return 1;
        });
    }
    
    /**
     * Validates list operations (add/remove)
     */
    protected boolean validateListOperation(PlayerPeekData data, UUID targetId, 
                                          net.minecraft.network.chat.Component targetName, ServerPlayer executor, boolean isAddOperation) {
        Map<UUID, Long> list = getList(data);
        boolean inList = list.containsKey(targetId);
        
        if (isAddOperation && inList) {
            executor.sendSystemMessage(Component.translatable("peek." + getListType() + ".already_exists", targetName)
                .withStyle(ChatFormatting.YELLOW), false);
            return false;
        } else if (!isAddOperation && !inList) {
            executor.sendSystemMessage(Component.translatable("peek." + getListType() + ".not_exists", targetName)
                .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        
        return true;
    }
    
    /**
     * Handles session and request management when adding to blacklist
     */
    private void handleBlacklistSessionsAndRequests(ServerPlayer player, ServerPlayer target) {
        PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
        PeekRequestManager requestManager = ManagerRegistry.getInstance().getManager(PeekRequestManager.class);
        
        // 1. If the blacklisted player is currently peeking the blacklister, stop their peek session
        if (sessionManager.isPlayerPeeking(target.getUUID())) {
            var targetSession = sessionManager.getSessionByPeeker(target.getUUID());
            if (targetSession != null && targetSession.getTargetId().equals(player.getUUID())) {
                // The blacklisted player is peeking the blacklister, stop the session
                sessionManager.stopPeekSession(
                    target.getUUID(),
                    false,
                    ServerPlayerCompat.getServer(player),
                    Component.translatable("peek.message.ended_blacklisted")
                );
                
                // Notify both players
                player.sendSystemMessage(Component.translatable("peek.message.blacklist_stopped_peek", target.getDisplayName()), false);
            }
        }
        
        // 2. Cancel any pending requests between the players
        requestManager.cancelRequestsBetween(player.getUUID(), target.getUUID());
    }
}

