package com.peek.utils;

import com.mojang.brigadier.context.CommandContext;
import com.peek.PeekMod;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
    public static ServerPlayerEntity getPlayerArgument(CommandContext<ServerCommandSource> context, String argName) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, argName);
            if (target == null) {
                Objects.requireNonNull(context.getSource().getPlayer()).sendMessage(Text.translatable("peek.error.player_not_found").formatted(Formatting.RED), false);
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
    public static Collection<ServerPlayerEntity> getPlayersArgument(CommandContext<ServerCommandSource> context, String argName) {
        try {
            return EntityArgumentType.getPlayers(context, argName);
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
    public static int executeCommand(CommandContext<ServerCommandSource> context, Supplier<Integer> command) {
        try {
            return command.get();
        } catch (Exception e) {
            context.getSource().sendError(Text.translatable("peek.error.internal"));
            return 0;
        }
    }
    
    /**
     * Execute a player-specific command
     * @param context Command context
     * @param command Command function taking a player
     * @return Command result
     */
    public static int executePlayerCommand(CommandContext<ServerCommandSource> context, 
                                         Function<ServerPlayerEntity, Integer> command) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            return command.apply(player);
        } catch (Exception e) {
            PeekMod.LOGGER.error(e.getMessage());
            context.getSource().sendError(Text.translatable("peek.error.internal"));
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
    public static <T> int executeWithPlayer(CommandContext<ServerCommandSource> context, String argName, 
                                          Function<ServerPlayerEntity, T> command,
                                          Function<T, Integer> resultHandler) {
        return executeCommand(context, () -> {
            ServerPlayerEntity player = getPlayerArgument(context, argName);
            if (player == null) {
                context.getSource().sendError(Text.translatable("peek.error.player_not_found"));
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
    public static Text getErrorMessage(String error) {
        if (error.contains(":")) {
            String[] parts = error.split(":", 2);
            if (parts[0].equals("ON_COOLDOWN")) {
                long remainingSeconds = Long.parseLong(parts[1]) / 1000;
                return Text.translatable("peek.error.on_cooldown", remainingSeconds).formatted(Formatting.RED);
            }
        }
        
        // The error parameter is expected to be a translation key (e.g., "peek.error.hostile_mobs_nearby")
        // Simply use it directly for translation
        return Text.translatable(error).formatted(Formatting.RED);
    }
    
    /**
     * Updates command tree for a player to reflect current game state
     * @param player Player to update commands for
     */
    public static void updateCommandTree(ServerPlayerEntity player) {
        try {
            player.getServer().getPlayerManager().sendCommandTree(player);
        } catch (Exception e) {
            // Ignore errors - command tree update is not critical
        }
    }
    
    /**
     * Updates command trees for both requester and target after request state changes
     * @param requester Requester player (can be null if offline)
     * @param target Target player (can be null if offline)
     */
    public static void updateCommandTreesForRequest(ServerPlayerEntity requester, ServerPlayerEntity target) {
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
        
        ServerPlayerEntity requester = server.getPlayerManager().getPlayer(requesterId);
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetId);
        
        updateCommandTreesForRequest(requester, target);
    }
}