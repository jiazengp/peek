package com.peek.utils;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.peek.command.suggestion.PeekSuggestions;
import com.peek.utils.permissions.Permissions;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Concrete implementation for building whitelist commands
 */
public class WhitelistCommandBuilder extends AbstractListCommandBuilder {
    
    private final WhitelistManager manager = new WhitelistManager();
    
    @Override
    protected String getCommandName() {
        return "whitelist";
    }
    
    @Override
    protected String getPermissionBase() {
        return Permissions.Command.WHITELIST;
    }
    
    @Override
    protected String getAddPermission() {
        return Permissions.Command.WHITELIST_ADD;
    }
    
    @Override
    protected String getRemovePermission() {
        return Permissions.Command.WHITELIST_REMOVE;
    }
    
    @Override
    protected String getListPermission() {
        return Permissions.Command.WHITELIST_LIST;
    }
    
    @Override
    protected AbstractPlayerListManager getManager() {
        return manager;
    }
    
    @Override
    protected SuggestionProvider<ServerCommandSource> getAddSuggestions() {
        return PeekSuggestions.WHITELIST_ADD_SUGGESTIONS;
    }
    
    @Override
    protected SuggestionProvider<ServerCommandSource> getRemoveSuggestions() {
        return PeekSuggestions.WHITELIST_REMOVE_SUGGESTIONS;
    }
}