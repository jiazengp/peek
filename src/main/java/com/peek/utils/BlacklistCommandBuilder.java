package com.peek.utils;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.peek.command.suggestion.PeekSuggestions;
import com.peek.utils.permissions.Permissions;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Concrete implementation for building blacklist commands
 */
public class BlacklistCommandBuilder extends AbstractListCommandBuilder {
    
    private final BlacklistManager manager = new BlacklistManager();
    
    @Override
    protected String getCommandName() {
        return "blacklist";
    }
    
    @Override
    protected String getPermissionBase() {
        return Permissions.Command.BLACKLIST;
    }
    
    @Override
    protected String getAddPermission() {
        return Permissions.Command.BLACKLIST_ADD;
    }
    
    @Override
    protected String getRemovePermission() {
        return Permissions.Command.BLACKLIST_REMOVE;
    }
    
    @Override
    protected String getListPermission() {
        return Permissions.Command.BLACKLIST_LIST;
    }
    
    @Override
    protected AbstractPlayerListManager getManager() {
        return manager;
    }
    
    @Override
    protected SuggestionProvider<ServerCommandSource> getAddSuggestions() {
        return PeekSuggestions.BLACKLIST_ADD_SUGGESTIONS;
    }
    
    @Override
    protected SuggestionProvider<ServerCommandSource> getRemoveSuggestions() {
        return PeekSuggestions.BLACKLIST_REMOVE_SUGGESTIONS;
    }
}