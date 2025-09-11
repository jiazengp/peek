package com.peek.command;

import com.mojang.brigadier.CommandDispatcher;
import com.peek.command.subcommands.PeekRequestCommands;
import com.peek.command.subcommands.PeekSettingsCommands;
import com.peek.command.subcommands.PeekUtilityCommands;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Main peek command implementation - now delegated to subcommand modules
 */
public class PeekCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("peek")
            // Request management commands
            .then(PeekRequestCommands.createPlayerCommand())
            .then(PeekRequestCommands.createAcceptCommand())
            .then(PeekRequestCommands.createDenyCommand())
            .then(PeekRequestCommands.createCancelCommand())
            .then(PeekRequestCommands.createStopCommand())
            .then(PeekRequestCommands.createWhoCommand())
            .then(PeekRequestCommands.createCancelPlayerCommand())
            
            // Settings commands
            .then(PeekSettingsCommands.createSettingsCommand())
            
            // Utility commands
            .then(PeekUtilityCommands.createStatsCommand())
            .then(PeekUtilityCommands.createInviteCommand())
            .then(PeekUtilityCommands.createDebugCommand())
            
            // Default usage
            .executes(PeekUtilityCommands::showUsage));
    }
}