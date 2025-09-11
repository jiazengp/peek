package com.peek.command.suggestion;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.peek.data.PeekDataStorage;
import com.peek.data.peek.PlayerPeekData;
import com.peek.config.ModConfigManager;
import com.peek.manager.ManagerRegistry;
import com.peek.manager.PeekSessionManager;
import eu.pb4.playerdata.api.PlayerDataApi;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Objects;
import java.util.function.Function;
import java.util.Map;
import java.util.UUID;

/**
 * Custom suggestion providers for Peek commands
 */
public class PeekSuggestions {
    
    /**
     * Suggests all players except the command executor, excluding players with pending requests
     */
    public static final SuggestionProvider<ServerCommandSource> PLAYER_SUGGESTIONS_EXCLUDING_SELF = 
        (context, builder) -> {
            ServerCommandSource source = context.getSource();
            if (!source.isExecutedByPlayer()) return builder.buildFuture();
            
            String input = builder.getRemaining().toLowerCase();
            
            try {
                ServerPlayerEntity requester = source.getPlayerOrThrow();
                
                source.getServer().getPlayerManager().getPlayerList().stream()
                    .filter(player -> !isExecutor(source, player))
                    .filter(player -> com.peek.utils.ValidationUtils.canSendPeekRequestTo(requester, player)) // Use player entity overload for private mode check
                    .map(player -> player.getName().getString())
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .forEach(builder::suggest);
                    
            } catch (Exception e) {
                // Fallback with basic filtering (no validation due to exception)
                source.getServer().getPlayerManager().getPlayerList().stream()
                    .filter(player -> !isExecutor(source, player))
                    .map(player -> player.getName().getString())
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .forEach(builder::suggest);
            }
            
            return builder.buildFuture();
        };
        
    /**
     * Suggests players for blacklist add - excludes self and already blacklisted players
     */
    public static final SuggestionProvider<ServerCommandSource> BLACKLIST_ADD_SUGGESTIONS = 
        createListAddSuggestions(PlayerPeekData::blacklist);
        
    /**
     * Suggests players for blacklist remove - only suggests currently blacklisted players
     */
    public static final SuggestionProvider<ServerCommandSource> BLACKLIST_REMOVE_SUGGESTIONS = 
        createListRemoveSuggestions(PlayerPeekData::blacklist);
        
    /**
     * Suggests players who are currently peeking the command executor - for cancel-player command
     */
    public static final SuggestionProvider<ServerCommandSource> CANCEL_PLAYER_SUGGESTIONS = 
        (context, builder) -> {
            ServerCommandSource source = context.getSource();
            if (!source.isExecutedByPlayer()) return builder.buildFuture();
            
            try {
                ServerPlayerEntity player = source.getPlayerOrThrow();
                String input = builder.getRemaining().toLowerCase();
                
                // Get the session manager from registry
                com.peek.manager.PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
                
                // Get all sessions where this player is the target
                sessionManager.getSessionsTargeting(player.getUuid()).stream()
                    .map(session -> source.getServer().getPlayerManager().getPlayer(session.getPeekerId()))
                    .filter(Objects::nonNull)
                    .map(p -> p.getName().getString())
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .forEach(builder::suggest);
                    
            } catch (Exception e) {
                // No suggestions if error
            }
            
            return builder.buildFuture();
        };
        
    /**
     * Suggests the requester name for accept/deny commands - shows who sent the pending request
     */
    public static final SuggestionProvider<ServerCommandSource> PENDING_REQUEST_SUGGESTIONS = 
        (context, builder) -> {
            ServerCommandSource source = context.getSource();
            if (!source.isExecutedByPlayer()) return builder.buildFuture();
            
            try {
                ServerPlayerEntity player = source.getPlayerOrThrow();
                String input = builder.getRemaining().toLowerCase();
                
                // Get pending request for this player
                com.peek.manager.PeekRequestManager requestManager = ManagerRegistry.getInstance().getManager(com.peek.manager.PeekRequestManager.class);
                com.peek.data.peek.PeekRequest request = requestManager.getPendingRequestForPlayer(player.getUuid());
                
                if (request != null) {
                    // Get the requester's name
                    ServerPlayerEntity requester = source.getServer().getPlayerManager().getPlayer(request.getRequesterId());
                    if (requester != null) {
                        String requesterName = requester.getName().getString();
                        if (requesterName.toLowerCase().startsWith(input)) {
                            builder.suggest(requesterName);
                        }
                    }
                }
                    
            } catch (Exception e) {
                // No suggestions if error
            }
            
            return builder.buildFuture();
        };
        
    /**
     * Suggests players for invite command - excludes self and players who have the inviter blacklisted
     */
    public static final SuggestionProvider<ServerCommandSource> INVITE_SUGGESTIONS = 
        (context, builder) -> {
            ServerCommandSource source = context.getSource();
            if (!source.isExecutedByPlayer()) return builder.buildFuture();
            
            String input = builder.getRemaining().toLowerCase();
            
            try {
                ServerPlayerEntity inviter = source.getPlayerOrThrow();
                PlayerPeekData inviterData = PlayerDataApi.getCustomDataFor(inviter, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
                if (inviterData == null) inviterData = com.peek.data.peek.PlayerPeekData.createDefault();
                
                // Get session manager and per-player session limit
                PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
                int maxSessionsPerPlayer = ModConfigManager.getMaxPeekSessionsPerPlayer();
                
                // Check if inviter has reached maximum sessions being peeked
                if (maxSessionsPerPlayer > 0) {
                    int inviterCurrentSessions = sessionManager.getSessionsTargeting(inviter.getUuid()).size();
                    if (inviterCurrentSessions >= maxSessionsPerPlayer) {
                        // No suggestions if inviter has reached session limit
                        return builder.buildFuture();
                    }
                }
                
                source.getServer().getPlayerManager().getPlayerList().stream()
                    .filter(player -> !isExecutor(source, player)) // Exclude self
                    .filter(player -> {
                        // Exclude players who have the inviter blacklisted
                        PlayerPeekData targetData = PlayerDataApi.getCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
                        if (targetData != null && targetData.blacklist().containsKey(inviter.getUuid())) {
                            return false;
                        }
                        
                        // Exclude players in private mode
                        if (targetData != null && targetData.privateMode()) {
                            return false;
                        }
                        
                        // Exclude players who are already peeking the inviter
                        if (sessionManager.isPlayerPeeking(player.getUuid())) {
                            var targetSession = sessionManager.getSessionByPeeker(player.getUuid());
                            return targetSession == null || !targetSession.getTargetId().equals(inviter.getUuid()); // Player is already peeking the inviter
                        }
                        
                        return true;
                    })
                    .map(player -> player.getName().getString())
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .forEach(builder::suggest);
                    
            } catch (Exception e) {
                // Fallback to basic suggestions excluding self
                source.getServer().getPlayerManager().getPlayerList().stream()
                    .filter(player -> !isExecutor(source, player))
                    .map(player -> player.getName().getString())
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .forEach(builder::suggest);
            }
            
            return builder.buildFuture();
        };
        
    /**
     * Creates a generic suggestion provider for adding players to a list
     * Excludes self and players already in the specified list
     */
    public static SuggestionProvider<ServerCommandSource> createListAddSuggestions(
            Function<PlayerPeekData, Map<UUID, Long>> listGetter) {
        return (context, builder) -> {
            ServerCommandSource source = context.getSource();
            if (!source.isExecutedByPlayer()) return builder.buildFuture();
            
            try {
                ServerPlayerEntity player = source.getPlayerOrThrow();
                PlayerPeekData data = PlayerDataApi.getCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
                if (data == null) data = PlayerPeekData.createDefault();
                
                final PlayerPeekData finalData = data;
                String input = builder.getRemaining().toLowerCase();
                
                source.getServer().getPlayerManager().getPlayerList().stream()
                    .filter(p -> !isExecutor(source, p))
                    .filter(p -> !listGetter.apply(finalData).containsKey(p.getUuid())) // Exclude already listed players
                    .map(p -> p.getName().getString())
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .forEach(builder::suggest);
                    
            } catch (Exception e) {
                // Fallback to regular suggestions
                return PLAYER_SUGGESTIONS_EXCLUDING_SELF.getSuggestions(context, builder);
            }
            
            return builder.buildFuture();
        };
    }
    
    /**
     * Creates a generic suggestion provider for removing players from a list
     * Only suggests players currently in the specified list
     */
    public static SuggestionProvider<ServerCommandSource> createListRemoveSuggestions(
            Function<PlayerPeekData, Map<UUID, Long>> listGetter) {
        return (context, builder) -> {
            ServerCommandSource source = context.getSource();
            if (!source.isExecutedByPlayer()) return builder.buildFuture();
            
            try {
                ServerPlayerEntity player = source.getPlayerOrThrow();
                PlayerPeekData data = PlayerDataApi.getCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
                if (data == null || listGetter.apply(data).isEmpty()) return builder.buildFuture();
                
                String input = builder.getRemaining().toLowerCase();
                
                // Suggest only listed players
                listGetter.apply(data).keySet().stream()
                    .map(uuid -> source.getServer().getPlayerManager().getPlayer(uuid))
                    .filter(Objects::nonNull)
                    .map(p -> p.getName().getString())
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .forEach(builder::suggest);
                    
            } catch (Exception e) {
                // No suggestions if error
            }
            
            return builder.buildFuture();
        };
    }
    
    // Specific implementations using the generic builders
    public static final SuggestionProvider<ServerCommandSource> WHITELIST_ADD_SUGGESTIONS = 
        createListAddSuggestions(PlayerPeekData::whitelist);
    
    public static final SuggestionProvider<ServerCommandSource> WHITELIST_REMOVE_SUGGESTIONS = 
        createListRemoveSuggestions(PlayerPeekData::whitelist);
    
    /**
     * Helper method to check if a player is the command executor
     */
    private static boolean isExecutor(ServerCommandSource source, ServerPlayerEntity player) {
        if (!source.isExecutedByPlayer()) return false;
        try {
            return source.getPlayerOrThrow().getUuid().equals(player.getUuid());
        } catch (Exception e) {
            return false;
        }
    }
}