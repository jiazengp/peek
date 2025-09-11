package com.peek.command.subcommands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.peek.command.suggestion.PeekSuggestions;
import com.peek.config.ModConfigManager;
import com.peek.data.PeekDataStorage;
import com.peek.data.peek.PeekRequest;
import com.peek.data.peek.PeekSession;
import com.peek.data.peek.PlayerPeekData;
import com.peek.manager.InviteManager;
import com.peek.manager.ManagerRegistry;
import com.peek.manager.PeekRequestManager;
import com.peek.manager.PeekSessionManager;
import com.peek.utils.*;
import com.peek.utils.compat.TextEventCompat;
import com.peek.utils.permissions.PermissionChecker;
import com.peek.utils.permissions.Permissions;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.parsers.NodeParser;
import eu.pb4.playerdata.api.PlayerDataApi;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Handles utility peek commands: stats, invite, debug, usage
 */
public class PeekUtilityCommands {
    
    public static LiteralArgumentBuilder<ServerCommandSource> createStatsCommand() {
        return CommandManager.literal("stats")
                .requires(source -> PermissionChecker.hasPermission(source, Permissions.Command.STATS, 0))
                .executes(ctx -> showStats(ctx, ctx.getSource().getPlayerOrThrow()))
                .then(CommandManager.argument("target", EntityArgumentType.player())
                    .requires(source -> PermissionChecker.hasPermission(source, Permissions.Command.STATS_OTHERS, 2))
                    .executes(ctx -> showStats(ctx, CommandUtils.getPlayerArgument(ctx, "target"))));
    }
    
    public static LiteralArgumentBuilder<ServerCommandSource> createInviteCommand() {
        return CommandManager.literal("invite")
                .requires(source -> PermissionChecker.hasPermission(source, Permissions.Command.INVITE, 0))
                .then(CommandManager.argument("players", EntityArgumentType.players())
                    .suggests(PeekSuggestions.INVITE_SUGGESTIONS)
                    .executes(PeekUtilityCommands::sendInvites));
    }
    
    public static LiteralArgumentBuilder<ServerCommandSource> createDebugCommand() {
        return CommandManager.literal("debug")
                .executes(PeekUtilityCommands::debugPlayerState);
    }
    
    public static int showUsage(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            ServerCommandSource source = context.getSource();
            java.util.List<String> availableCommands = new java.util.ArrayList<>();
            
            // Check which commands are available based on player's current state
            if (ValidationUtils.canSendPeekRequestWithPermission(source, Permissions.Command.PEEK, 0)) {
                availableCommands.add("/peek player <target> - Send peek request");
            }
            
            if (ValidationUtils.requiresPendingRequestWithPermission(source, Permissions.Command.ACCEPT)) {
                availableCommands.add("/peek accept [requester] - Accept peek request");
                availableCommands.add("/peek deny [requester] - Deny peek request");
            }
            
            if (ValidationUtils.requiresPendingRequestAsRequester(source)) {
                availableCommands.add("/peek cancel - Cancel your peek request");
            }
            
            if (ValidationUtils.requiresActivePeekerWithPermission(source, Permissions.Command.STOP)) {
                availableCommands.add("/peek stop - Stop current peek");
            }
            
            if (ValidationUtils.requiresBeingPeeked(source)) {
                availableCommands.add("/peek who - See who's peeking you");
                availableCommands.add("/peek cancel-player <player> - Cancel specific player's peek");
            }
            
            if (PermissionChecker.hasPermission(source, Permissions.Command.STATS, 0)) {
                if (PermissionChecker.hasPermission(source, Permissions.Command.STATS_OTHERS, 2)) {
                    availableCommands.add("/peek stats [player] - View statistics");
                } else {
                    availableCommands.add("/peek stats - View your statistics");
                }
            }
            
            if (PermissionChecker.hasPermission(source, Permissions.Command.SETTINGS, 0)) {
                availableCommands.add("/peek settings - Manage settings");
            }
            
            if (PermissionChecker.hasPermission(source, Permissions.Command.BLACKLIST, 0)) {
                availableCommands.add("/peek blacklist - Manage blacklist");
            }
            
            MutableText usage = Text.translatable("peek.command.usage.header");
            if (availableCommands.isEmpty()) {
                usage.append(Text.translatable("peek.command.usage.none"));
            } else {
                for (String cmd : availableCommands) {
                    usage.append(Text.literal("§e\n" + cmd));
                }
            }
            
            player.sendMessage(usage, false);
            return 1;
        });
    }
    
    private static int showStats(CommandContext<ServerCommandSource> context, ServerPlayerEntity target) {
        return CommandUtils.executePlayerCommand(context, (viewer) -> {
            if (!ValidationUtils.validatePlayerNotNull(target, viewer)) return 0;
            
            // Create a NodeParser for placeholder processing
            var parser = NodeParser.builder()
                    .globalPlaceholders()
                    .quickText()
                    .build();
            
            PlaceholderContext placeholderContext = PlaceholderContext.of(target);
            
            // Header with translation and placeholder
            String headerTemplate = Text.translatable("peek.stats.placeholder.header", "%player:name%").getString();
            Text headerText = parser.parseText(headerTemplate, placeholderContext.asParserContext());
            viewer.sendMessage(headerText, false);
            
            // Basic stats using translation keys and placeholders
            String peekCountTemplate = Text.translatable("peek.stats.placeholder.peek_count", "%peekmod:peek_count%").getString();
            Text peekCountText = parser.parseText(peekCountTemplate, placeholderContext.asParserContext());
            viewer.sendMessage(peekCountText, false);
            
            String peekedCountTemplate = Text.translatable("peek.stats.placeholder.peeked_count", "%peekmod:peeked_count%").getString();
            Text peekedCountText = parser.parseText(peekedCountTemplate, placeholderContext.asParserContext());
            viewer.sendMessage(peekedCountText, false);
            
            String durationTemplate = Text.translatable("peek.stats.placeholder.total_duration", "%peekmod:total_duration%").getString();
            Text durationText = parser.parseText(durationTemplate, placeholderContext.asParserContext());
            viewer.sendMessage(durationText, false);
            
            // Current status using translation keys and placeholders
            String statusTemplate = Text.translatable("peek.stats.placeholder.currently_peeking", "%peekmod:is_peeking%").getString();
            Text statusText = parser.parseText(statusTemplate, placeholderContext.asParserContext());
            viewer.sendMessage(statusText, false);
            
            String privateModeTemplate = Text.translatable("peek.stats.placeholder.private_mode", "%peekmod:is_private%").getString();
            Text privateModeText = parser.parseText(privateModeTemplate, placeholderContext.asParserContext());
            viewer.sendMessage(privateModeText, false);
            
            return 1;
        });
    }
    
    private static int sendInvites(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            Collection<ServerPlayerEntity> targets = CommandUtils.getPlayersArgument(context, "players");
            
            // Check invite cooldown
            CooldownManager cooldownManager = CooldownManager.getInstance();
            if (cooldownManager.isOnInviteCooldown(player)) {
                long remainingSeconds = cooldownManager.getRemainingInviteCooldown(player.getUuid());
                player.sendMessage(Text.translatable("peek.message.invite_cooldown", remainingSeconds), false);
                return 0;
            }
            
            // Check max invite count
            int maxInvites = ModConfigManager.getMaxInviteCount();
            if (targets.size() > maxInvites) {
                player.sendMessage(Text.translatable("peek.message.invite_too_many", maxInvites), false);
                return 0;
            }
            
            // Get session manager and per-player session limit
            PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
            int maxSessionsPerPlayer = ModConfigManager.getMaxPeekSessionsPerPlayer();
            
            // Check if inviter has reached maximum sessions being peeked
            if (maxSessionsPerPlayer > 0) {
                int inviterCurrentSessions = sessionManager.getSessionsTargeting(player.getUuid()).size();
                if (inviterCurrentSessions >= maxSessionsPerPlayer) {
                    player.sendMessage(Text.translatable("peek.message.invite_inviter_session_limit", maxSessionsPerPlayer), false);
                    return 0;
                }
            }
            
            // Separate targets into blacklisted and non-blacklisted
            PlayerPeekData inviterData = getOrCreatePlayerData(player);
            List<ServerPlayerEntity> blacklistedTargets = new ArrayList<>();
            List<ServerPlayerEntity> nonBlacklistedTargets = new ArrayList<>();
            
            // First pass: separate blacklisted and non-blacklisted targets
            for (ServerPlayerEntity target : targets) {
                if (inviterData.blacklist().containsKey(target.getUuid())) {
                    blacklistedTargets.add(target);
                } else {
                    nonBlacklistedTargets.add(target);
                }
            }
            
            // Process non-blacklisted targets first
            List<ServerPlayerEntity> validTargets = nonBlacklistedTargets.stream()
                .filter(target -> {
                    if (target.equals(player)) {
                        return false; // Can't invite self
                    }
                    PlayerPeekData targetData = getOrCreatePlayerData(target);
                    if (targetData.blacklist().containsKey(player.getUuid())) {
                        return false; // Inviter is blacklisted by target
                    }
                    if (targetData.privateMode()) {
                        return false; // Target is in private mode
                    }
                    
                    // Check if target is already peeking the inviter
                    if (sessionManager.isPlayerPeeking(target.getUuid())) {
                        var targetSession = sessionManager.getSessionByPeeker(target.getUuid());
                        return targetSession == null || !targetSession.getTargetId().equals(player.getUuid()); // Target is already peeking the inviter
                    }
                    
                    return true;
                })
                .toList();
            
            int invitesSent = 0;
            
            // Process valid (non-blacklisted) targets if any exist
            if (!validTargets.isEmpty()) {
                // Set cooldown
                cooldownManager.setInviteCooldown(player.getUuid(), ModConfigManager.getInviteCooldownSeconds());
                
                // Send invites to valid targets and record them
                InviteManager inviteManager = ManagerRegistry.getInstance().getManager(InviteManager.class);
                for (ServerPlayerEntity target : validTargets) {
                    // Record the invitation
                    inviteManager.createInvite(player, target);
                    // Send the invite message
                    sendInviteMessage(player, target);
                }
                
                // Confirm to inviter
                player.sendMessage(Text.translatable("peek.message.invite_sent", 
                    validTargets.size(), 
                    validTargets.stream().map(p -> p.getName().getString()).reduce((a, b) -> a + ", " + b).orElse("")), false);
                
                invitesSent = validTargets.size();
            }
            
            // Handle blacklisted targets if any exist
            if (!blacklistedTargets.isEmpty()) {
                if (invitesSent > 0) {
                    // Add spacing if we already sent invites
                    player.sendMessage(Text.literal(""), false);
                }
                handleBlacklistedInviteTargets(player, blacklistedTargets);
            } else if (validTargets.isEmpty()) {
                // No valid targets and no blacklisted targets
                player.sendMessage(Text.translatable("peek.message.invite_no_valid_targets"), false);
                return 0;
            }
            
            return invitesSent;
        });
    }
    
    private static void sendInviteMessage(ServerPlayerEntity inviter, ServerPlayerEntity target) {
        // Create clickable button for peek command
        MutableText inviteMessage = Text.translatable("peek.message.invite_received", inviter.getName().getString());
        
        MutableText peekButton = Text.translatable("peek.command.invite.accept_button")
            .styled(style -> style
                .withClickEvent(TextEventCompat.runCommand("/peek player " + inviter.getName().getString()))
                .withHoverEvent(TextEventCompat.showText(
                    Text.translatable("peek.command.invite.hover_text", inviter.getName().getString()))));
        
        target.sendMessage(inviteMessage.append(peekButton), false);
        
        // Play invite received sound
        SoundManager.playInviteReceivedSound(target);
    }
    
    private static int debugPlayerState(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            UUID playerId = player.getUuid();
            PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
            PeekRequestManager requestManager = ManagerRegistry.getInstance().getManager(PeekRequestManager.class);
            
            MutableText message = Text.translatable("peek.debug.header", player.getName().getString());
            
            // Check peek states
            boolean isPeeking = sessionManager.isPlayerPeeking(playerId);
            boolean isBeingPeeked = sessionManager.isPlayerBeingPeeked(playerId);
            List<PeekSession> targetingSessions = sessionManager.getSessionsTargeting(playerId);
            
            message.append(Text.translatable("peek.debug.is_peeking", isPeeking));
            message.append(Text.translatable("peek.debug.is_being_peeked", isBeingPeeked));
            message.append(Text.translatable("peek.debug.targeting_sessions", targetingSessions.size()));
            
            for (PeekSession session : targetingSessions) {
                message.append(Text.translatable("peek.debug.session_info", 
                    session.getId(), session.getPeekerName()));
            }
            
            // Check request states
            boolean hasPendingAsRequester = requestManager.hasPendingRequestAsRequester(playerId);
            PeekRequest pendingRequest = requestManager.getPendingRequestForPlayer(playerId);
            
            message.append(Text.translatable("peek.debug.pending_as_requester", hasPendingAsRequester));
            message.append(Text.translatable("peek.debug.pending_as_target", (pendingRequest != null)));
            
            // Check validation methods
            ServerCommandSource source = context.getSource();
            boolean requiresBeingPeeked = ValidationUtils.requiresBeingPeeked(source);
            boolean requiresActivePeeker = ValidationUtils.requiresActivePeekerWithPermission(source, Permissions.Command.STOP);
            boolean requiresPendingRequest = ValidationUtils.requiresPendingRequestWithPermission(source, Permissions.Command.ACCEPT);
            boolean requiresPendingRequestAsRequester = ValidationUtils.requiresPendingRequestAsRequester(source);
            
            message.append(Text.translatable("peek.debug.command_visibility"));
            message.append(Text.translatable("peek.debug.requires_being_peeked", requiresBeingPeeked));
            message.append(Text.translatable("peek.debug.requires_active_peeker", requiresActivePeeker));
            message.append(Text.translatable("peek.debug.requires_pending_request", requiresPendingRequest));
            message.append(Text.translatable("peek.debug.requires_pending_as_requester", requiresPendingRequestAsRequester));
            
            player.sendMessage(message, false);
            return 1;
        });
    }
    
    /**
     * Handles the case where a player tries to invite someone in their blacklist
     */
    private static void handleBlacklistedInviteTargets(ServerPlayerEntity inviter, List<ServerPlayerEntity> blacklistedTargets) {
        if (blacklistedTargets.size() == 1) {
            // Single target case
            ServerPlayerEntity target = blacklistedTargets.getFirst();
            MutableText message = Text.translatable("peek.invite.blacklisted_single", target.getName().getString());
            
            // Add two separate buttons: remove from blacklist and invite
            MutableText removeButton = Text.translatable("peek.invite.remove_button")
                .styled(style -> style
                    .withClickEvent(TextEventCompat.runCommand("/peek settings blacklist remove " + target.getName().getString()))
                    .withHoverEvent(TextEventCompat.showText(Text.translatable("peek.invite.remove_tip", target.getName().getString()))));
            
            MutableText inviteButton = Text.translatable("peek.invite.send_button")
                .styled(style -> style
                    .withClickEvent(TextEventCompat.runCommand("/peek invite " + target.getName().getString()))
                    .withHoverEvent(TextEventCompat.showText(Text.translatable("peek.invite.send_tip", target.getName().getString()))));
            
            inviter.sendMessage(message.append(" ").append(removeButton).append(" ").append(inviteButton), false);
        } else {
            // Multiple targets case
            MutableText message = Text.translatable("peek.invite.blacklisted_multiple", blacklistedTargets.size());
            
            for (int i = 0; i < blacklistedTargets.size(); i++) {
                ServerPlayerEntity target = blacklistedTargets.get(i);
                MutableText targetInfo = Text.literal("\n§7" + (i + 1) + ". §f" + target.getName().getString() + " ");
                
                MutableText removeButton = Text.translatable("peek.invite.remove_button")
                    .styled(style -> style
                        .withClickEvent(TextEventCompat.runCommand("/peek settings blacklist remove " + target.getName().getString()))
                        .withHoverEvent(TextEventCompat.showText(Text.translatable("peek.invite.remove_tip", target.getName().getString()))));
                
                MutableText inviteButton = Text.translatable("peek.invite.send_button")
                    .styled(style -> style
                        .withClickEvent(TextEventCompat.runCommand("/peek invite " + target.getName().getString()))
                        .withHoverEvent(TextEventCompat.showText(Text.translatable("peek.invite.send_tip", target.getName().getString()))));
                
                message.append(targetInfo).append(removeButton).append(" ").append(inviteButton);
            }
            
            inviter.sendMessage(message, false);
        }
    }
    
    // Helper method
    private static PlayerPeekData getOrCreatePlayerData(ServerPlayerEntity player) {
        PlayerPeekData data = PlayerDataApi.getCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
        return data != null ? data : PlayerPeekData.createDefault();
    }
}