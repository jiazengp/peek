package com.peek.utils;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.peek.utils.permissions.PermissionChecker;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Abstract base class for building player list commands (blacklist/whitelist)
 * Provides common command structure that can be reused between different list types
 */
public abstract class AbstractListCommandBuilder {
    
    /**
     * Gets the command name for this list type
     * @return "blacklist" or "whitelist"
     */
    protected abstract String getCommandName();
    
    /**
     * Gets the base permission for this list type
     * @return Base permission string
     */
    protected abstract String getPermissionBase();
    
    /**
     * Gets the add permission for this list type
     * @return Add permission string
     */
    protected abstract String getAddPermission();
    
    /**
     * Gets the remove permission for this list type
     * @return Remove permission string
     */
    protected abstract String getRemovePermission();
    
    /**
     * Gets the list permission for this list type
     * @return List permission string
     */
    protected abstract String getListPermission();
    
    /**
     * Gets the manager for this list type
     * @return The list manager
     */
    protected abstract AbstractPlayerListManager getManager();
    
    /**
     * Gets the suggestion provider for add operations
     * @return Suggestion provider for valid players to add
     */
    protected abstract SuggestionProvider<ServerCommandSource> getAddSuggestions();
    
    /**
     * Gets the suggestion provider for remove operations
     * @return Suggestion provider for players in the list
     */
    protected abstract SuggestionProvider<ServerCommandSource> getRemoveSuggestions();
    
    /**
     * Creates the complete command structure for this list type
     * @return The command builder
     */
    public LiteralArgumentBuilder<ServerCommandSource> createCommand() {
        return CommandManager.literal(getCommandName())
                .requires(source -> PermissionChecker.hasPermission(source, getPermissionBase(), 0))
                .executes(context -> getManager().handleListCommand(context)) // Default behavior: show list
                .then(createAddSubcommand())
                .then(createRemoveSubcommand())
                .then(createListSubcommand());
    }
    
    /**
     * Creates the add subcommand
     */
    private LiteralArgumentBuilder<ServerCommandSource> createAddSubcommand() {
        return CommandManager.literal("add")
                .requires(source -> PermissionChecker.hasPermission(source, getAddPermission(), 0))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .suggests(getAddSuggestions())
                    .executes(context -> getManager().handleAddCommand(context)));
    }
    
    /**
     * Creates the remove subcommand
     */
    private LiteralArgumentBuilder<ServerCommandSource> createRemoveSubcommand() {
        return CommandManager.literal("remove")
                .requires(source -> PermissionChecker.hasPermission(source, getRemovePermission(), 0))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .suggests(getRemoveSuggestions())
                    .executes(context -> getManager().handleRemoveCommand(context)));
    }
    
    /**
     * Creates the list subcommand
     */
    private LiteralArgumentBuilder<ServerCommandSource> createListSubcommand() {
        return CommandManager.literal("list")
                .requires(source -> PermissionChecker.hasPermission(source, getListPermission(), 0))
                .executes(context -> getManager().handleListCommand(context));
    }
}