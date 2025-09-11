package com.peek.command.subcommands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.peek.command.suggestion.PeekSuggestions;
import com.peek.data.peek.PeekRequest;
import com.peek.data.peek.PeekSession;
import com.peek.manager.ManagerRegistry;
import com.peek.manager.PeekRequestManager;
import com.peek.manager.PeekSessionManager;
import com.peek.utils.CommandUtils;
import com.peek.manager.constants.PeekConstants;
import com.peek.utils.TextUtils;
import com.peek.utils.ValidationUtils;
import com.peek.utils.compat.TextEventCompat;
import com.peek.utils.permissions.Permissions;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Handles peek request related commands: player, accept, deny, cancel, stop, who, cancel-player
 */
public class PeekRequestCommands {
    
    public static LiteralArgumentBuilder<ServerCommandSource> createPlayerCommand() {
        return CommandManager.literal("player")
                .requires(source -> ValidationUtils.canSendPeekRequestWithPermission(source, Permissions.Command.PEEK, 0))
                .then(CommandManager.argument("target", EntityArgumentType.player())
                    .suggests(PeekSuggestions.PLAYER_SUGGESTIONS_EXCLUDING_SELF)
                    .executes(PeekRequestCommands::sendPeekRequest));
    }
    
    public static LiteralArgumentBuilder<ServerCommandSource> createAcceptCommand() {
        return CommandManager.literal("accept")
                .requires(source -> ValidationUtils.requiresPendingRequestWithPermission(source, Permissions.Command.ACCEPT))
                .executes(PeekRequestCommands::acceptRequest)
                .then(CommandManager.argument("requester", EntityArgumentType.player())
                    .suggests(PeekSuggestions.PENDING_REQUEST_SUGGESTIONS)
                    .executes(PeekRequestCommands::acceptRequest));
    }
    
    public static LiteralArgumentBuilder<ServerCommandSource> createDenyCommand() {
        return CommandManager.literal("deny")
                .requires(source -> ValidationUtils.requiresPendingRequestWithPermission(source, Permissions.Command.DENY))
                .executes(PeekRequestCommands::denyRequest)
                .then(CommandManager.argument("requester", EntityArgumentType.player())
                    .suggests(PeekSuggestions.PENDING_REQUEST_SUGGESTIONS)
                    .executes(PeekRequestCommands::denyRequest));
    }
    
    public static LiteralArgumentBuilder<ServerCommandSource> createCancelCommand() {
        return CommandManager.literal("cancel")
                .requires(ValidationUtils::requiresPendingRequestAsRequester)
                .executes(PeekRequestCommands::cancelRequest);
    }
    
    public static LiteralArgumentBuilder<ServerCommandSource> createStopCommand() {
        return CommandManager.literal("stop")
                .requires(source -> ValidationUtils.requiresActivePeekerWithPermission(source, Permissions.Command.STOP))
                .executes(PeekRequestCommands::stopPeek);
    }
    
    public static LiteralArgumentBuilder<ServerCommandSource> createWhoCommand() {
        return CommandManager.literal("who")
                .requires(ValidationUtils::requiresBeingPeeked)
                .executes(PeekRequestCommands::showWhoPeeking);
    }
    
    public static LiteralArgumentBuilder<ServerCommandSource> createCancelPlayerCommand() {
        return CommandManager.literal("cancel-player")
                .requires(ValidationUtils::requiresBeingPeeked)
                .then(CommandManager.argument("peeker", EntityArgumentType.player())
                    .suggests(PeekSuggestions.CANCEL_PLAYER_SUGGESTIONS)
                    .executes(PeekRequestCommands::cancelSpecificPeek));
    }
    
    private static int sendPeekRequest(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            ServerPlayerEntity target = CommandUtils.getPlayerArgument(context, "target");
            if (!ValidationUtils.validatePlayerNotNull(target, player) || target == null) return 0;

            // Check specifically for pending request conflicts first (for better error messages)
            PeekRequestManager requestManager = ManagerRegistry.getInstance().getManager(PeekRequestManager.class);
            PeekRequest existingRequest = requestManager.getPendingRequestBetween(player.getUuid(), target.getUuid());
            if (existingRequest != null) {
                long remainingTime = existingRequest.getRemainingSeconds();
                Text message = Text.literal("§e" + Text.translatable("peek.error.request_pending_wait", target.getName().getString(), remainingTime).getString());
                player.sendMessage(message, false);
                return 0;
            }

            PeekConstants.Result<PeekRequest> result = requestManager.sendRequest(player, target);
            if (!ValidationUtils.validateResult(result, player)) return 0;
            
            return 1;
        });
    }
    
    private static int acceptRequest(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            PeekRequest request;
            
            // Check if requester argument was provided
            try {
                ServerPlayerEntity requester = CommandUtils.getPlayerArgument(context, "requester");
                request = ValidationUtils.validatePendingRequest(player.getUuid(), player);
                if (requester != null) {
                    // Validate that this specific player has sent a request to us
                    if (request == null || !request.getRequesterId().equals(requester.getUuid())) {
                        player.sendMessage(Text.literal("§c" + Text.translatable("peek.error.no_request_from_player", requester.getName().getString()).getString()), false);
                        return 0;
                    }
                } else {
                    // No requester specified, use the first pending request (existing behavior)
                    if (request == null) return 0;
                }
            } catch (Exception e) {
                // No requester argument provided, use existing logic
                request = ValidationUtils.validatePendingRequest(player.getUuid(), player);
                if (request == null) return 0;
            }
            
            PeekConstants.Result<PeekRequest> result = ManagerRegistry.getInstance().getManager(PeekRequestManager.class).acceptRequest(player, request.getId());
            if (!ValidationUtils.validateResult(result, player)) return 0;
            
            return 1;
        });
    }
    
    private static int denyRequest(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            PeekRequest request;
            
            // Check if requester argument was provided
            try {
                ServerPlayerEntity requester = CommandUtils.getPlayerArgument(context, "requester");
                request = ValidationUtils.validatePendingRequest(player.getUuid(), player);
                if (requester != null) {
                    // Validate that this specific player has sent a request to us
                    if (request == null || !request.getRequesterId().equals(requester.getUuid())) {
                        player.sendMessage(Text.literal("§c" + Text.translatable("peek.error.no_request_from_player", requester.getName().getString()).getString()), false);
                        return 0;
                    }
                } else {
                    // No requester specified, use the first pending request (existing behavior)
                    if (request == null) return 0;
                }
            } catch (Exception e) {
                // No requester argument provided, use existing logic
                request = ValidationUtils.validatePendingRequest(player.getUuid(), player);
                if (request == null) return 0;
            }
            
            ManagerRegistry.getInstance().getManager(PeekRequestManager.class).denyRequest(player, request.getId());
            return 1;
        });
    }
    
    private static int cancelRequest(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            // Get the player's pending request as requester
            PeekRequest request = ManagerRegistry.getInstance().getManager(PeekRequestManager.class).getPendingRequestAsRequester(player.getUuid());
            if (request == null) {
                player.sendMessage(Text.literal("§c" + Text.translatable("peek.error.no_pending_request").getString()), false);
                return 0;
            }
            
            // Cancel the request
            PeekConstants.Result<PeekRequest> result = ManagerRegistry.getInstance().getManager(PeekRequestManager.class).cancelRequest(player, request.getId());
            if (!ValidationUtils.validateResult(result, player)) return 0;
            
            // Simple success confirmation for cancel operation

            if (player.getServer() == null) {
                return 0;
            }

            ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(request.getTargetId());
            Text targetName = target != null ? target.getDisplayName() : Text.translatable("argument.player.unknown");
            player.sendMessage(Text.translatable("peek.request_cancelled", targetName), false);
            
            return 1;
        });
    }
    
    private static int stopPeek(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            PeekConstants.Result<String> result = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).stopPeekSession(player.getUuid(), false, player.getServer());
            
            if (result.isSuccess()) {
                // Simple success confirmation for stop operation
                player.sendMessage(Text.translatable("peek.message.ended_normal"), false);
                return 1;
            } else {
                return ValidationUtils.validateResult(result, player) ? 1 : 0;
            }
        });
    }
    
    private static int showWhoPeeking(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            List<PeekSession> targetingSessions = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).getSessionsTargeting(player.getUuid());
            
            if (!ValidationUtils.validateCollectionNotEmpty(targetingSessions, 
                    () -> Text.translatable("peek.message.no_one_peeking"), player)) {
                return 1;
            }
            
            MutableText message = Text.translatable("peek.message.who_peeking_header", targetingSessions.size());
            
            for (PeekSession session : targetingSessions) {
                MutableText sessionInfo = Text.translatable("peek.command.who.session_info",
                    session.getPeekerName(), TextUtils.formatDuration(session.getDurationSeconds()));
                
                // Add cancel button
                MutableText cancelButton = Text.translatable("peek.command.who.cancel_button")
                    .styled(style -> style
                        .withClickEvent(TextEventCompat.runCommand("/peek cancel-player " + session.getPeekerName()))
                        .withHoverEvent(TextEventCompat.showText(Text.translatable("peek.message.manage.cancel.tip"))));
                
                message.append(sessionInfo.append(cancelButton));
            }
            
            player.sendMessage(message, false);
            return 1;
        });
    }
    
    private static int cancelSpecificPeek(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            ServerPlayerEntity peekerPlayer = CommandUtils.getPlayerArgument(context, "peeker");
            if (!ValidationUtils.validatePlayerNotNull(peekerPlayer, player) || peekerPlayer == null) return 0;

            PeekSession session = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).getSessionByPeeker(peekerPlayer.getUuid());
            if (!ValidationUtils.validateSessionRelationship(session, player.getUuid(), peekerPlayer.getDisplayName(), player)) {
                return 0;
            }
            
            PeekConstants.Result<String> result = ManagerRegistry.getInstance().getManager(PeekSessionManager.class)
                .stopPeekSession(peekerPlayer.getUuid(), false, player.getServer());
            
            if (result.isSuccess()) {
                // No need to confirm to player - they initiated the kick
                // Only notify the peeker they were kicked
                peekerPlayer.sendMessage(Text.translatable("peek.message.cancelled_by_target", 
                    player.getDisplayName()), false);
                return 1;
            } else {
                return ValidationUtils.validateResult(result, player) ? 1 : 0;
            }
        });
    }
}