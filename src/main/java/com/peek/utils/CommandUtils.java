package com.peek.utils;

import com.mojang.brigadier.context.CommandContext;
import com.peek.PeekMod;
import com.peek.utils.compat.ServerPlayerCompat;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utility methods for command execution and player handling
 */
public class CommandUtils {
    
    /**
     * Safely get a player argument from command context
     * @param context Command context
     * @param argName Argument name
     * @return Player entity or null if not found
     */
    public static ServerPlayer getPlayerArgument(CommandContext<CommandSourceStack> context, String argName) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, argName);
            if (target == null) {
                Objects.requireNonNull(context.getSource().getPlayer()).sendSystemMessage(Component.translatable("peek.error.player_not_found").withStyle(ChatFormatting.RED), false);
                return null;
            }
            return target;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Safely get multiple player arguments from command context
     * @param context Command context
     * @param argName Argument name
     * @return Collection of player entities
     */
    public static Collection<ServerPlayer> getPlayersArgument(CommandContext<CommandSourceStack> context, String argName) {
        try {
            return EntityArgument.getPlayers(context, argName);
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }
    
    /**
     * Execute a command with error handling
     * @param context Command context
     * @param command Command to execute
     * @return Command result
     */
    public static int executeCommand(CommandContext<CommandSourceStack> context, Supplier<Integer> command) {
        try {
            return command.get();
        } catch (Exception e) {
            context.getSource().sendFailure(Component.translatable("peek.error.internal"));
            return 0;
        }
    }
    
    /**
     * Execute a player-specific command
     * @param context Command context
     * @param command Command function taking a player
     * @return Command result
     */
    public static int executePlayerCommand(CommandContext<CommandSourceStack> context, 
                                         Function<ServerPlayer, Integer> command) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            return command.apply(player);
        } catch (Exception e) {
            PeekMod.LOGGER.error(e.getMessage());
            context.getSource().sendFailure(Component.translatable("peek.error.internal"));
            return 0;
        }
    }
    
    /**
     * Execute a command with a player argument
     * @param context Command context
     * @param argName Player argument name
     * @param command Command function taking a player
     * @param resultHandler Result handler function
     * @return Command result
     */
    public static <T> int executeWithPlayer(CommandContext<CommandSourceStack> context, String argName, 
                                          Function<ServerPlayer, T> command,
                                          Function<T, Integer> resultHandler) {
        return executeCommand(context, () -> {
            ServerPlayer player = getPlayerArgument(context, argName);
            if (player == null) {
                context.getSource().sendFailure(Component.translatable("peek.error.player_not_found"));
                return 0;
            }
            T result = command.apply(player);
            return resultHandler.apply(result);
        });
    }
    
    
    /**
     * Get formatted error message for peek error codes
     * @param error Error code
     * @return Formatted error text
     */
    public static Component getErrorMessage(String error) {
        if (error.contains(":")) {
            String[] parts = error.split(":", 2);
            if (parts[0].equals("ON_COOLDOWN")) {
                long remainingSeconds = Long.parseLong(parts[1]) / 1000;
                return Component.translatable("peek.error.on_cooldown", remainingSeconds).withStyle(ChatFormatting.RED);
            }
        }
        
        // The error parameter is expected to be a translation key (e.g., "peek.error.hostile_mobs_nearby")
        // Simply use it directly for translation
        return Component.translatable(error).withStyle(ChatFormatting.RED);
    }
    
    /**
     * Updates command tree for a player to reflect current game state
     * @param player Player to update commands for
     */
    public static void updateCommandTree(ServerPlayer player) {
        try {
            ServerPlayerCompat.getServer(player).getCommands().sendCommands(player);
        } catch (Exception e) {
            // Ignore errors - command tree update is not critical
        }
    }
    
    /**
     * Updates command trees for both requester and target after request state changes
     * @param requester Requester player (can be null if offline)
     * @param target Target player (can be null if offline)
     */
    public static void updateCommandTreesForRequest(ServerPlayer requester, ServerPlayer target) {
        if (requester != null) {
            updateCommandTree(requester); // Update cancel command visibility
        }
        if (target != null) {
            updateCommandTree(target); // Update accept/deny commands visibility  
        }
    }
    
    /**
     * Updates command trees for both requester and target after request state changes
     * Fallback method when only UUIDs are available
     * @param server Server instance
     * @param requesterId Requester UUID
     * @param targetId Target UUID
     */
    public static void updateCommandTreesForRequest(MinecraftServer server, java.util.UUID requesterId, java.util.UUID targetId) {
        if (server == null) return;
        
        ServerPlayer requester = server.getPlayerList().getPlayer(requesterId);
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        
        updateCommandTreesForRequest(requester, target);
    }
}


