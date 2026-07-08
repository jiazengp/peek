package com.peek.command.subcommands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.peek.command.suggestion.PeekSuggestions;
import com.peek.data.peek.PeekRequest;
import com.peek.data.peek.PeekSession;
import com.peek.manager.ManagerRegistry;
import com.peek.manager.PeekRequestManager;
import com.peek.manager.PeekSessionManager;
import com.peek.utils.CommandRequestProcessor;
import com.peek.utils.CommandUtils;
import com.peek.manager.constants.PeekConstants;
import com.peek.utils.TextUtils;
import com.peek.utils.ValidationUtils;
import com.peek.utils.compat.ServerPlayerCompat;
import com.peek.utils.compat.TextEventCompat;
import com.peek.utils.permissions.Permissions;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Handles peek request related commands: player, accept, deny, cancel, stop, who, cancel-player
 */
public class PeekRequestCommands {
    
    public static LiteralArgumentBuilder<CommandSourceStack> createPlayerCommand() {
        return Commands.literal("player")
                .requires(source -> ValidationUtils.canSendPeekRequestWithPermission(source, Permissions.Command.PEEK, 0))
                .then(Commands.argument("target", EntityArgument.player())
                    .suggests(PeekSuggestions.PLAYER_SUGGESTIONS_EXCLUDING_SELF)
                    .executes(PeekRequestCommands::sendPeekRequest));
    }
    
    public static LiteralArgumentBuilder<CommandSourceStack> createAcceptCommand() {
        return Commands.literal("accept")
                .requires(source -> ValidationUtils.requiresPendingRequestWithPermission(source, Permissions.Command.ACCEPT))
                .executes(PeekRequestCommands::acceptRequest)
                .then(Commands.argument("requester", EntityArgument.player())
                    .suggests(PeekSuggestions.PENDING_REQUEST_SUGGESTIONS)
                    .executes(PeekRequestCommands::acceptRequest));
    }
    
    public static LiteralArgumentBuilder<CommandSourceStack> createDenyCommand() {
        return Commands.literal("deny")
                .requires(source -> ValidationUtils.requiresPendingRequestWithPermission(source, Permissions.Command.DENY))
                .executes(PeekRequestCommands::denyRequest)
                .then(Commands.argument("requester", EntityArgument.player())
                    .suggests(PeekSuggestions.PENDING_REQUEST_SUGGESTIONS)
                    .executes(PeekRequestCommands::denyRequest));
    }
    
    public static LiteralArgumentBuilder<CommandSourceStack> createCancelCommand() {
        return Commands.literal("cancel")
                .requires(ValidationUtils::requiresPendingRequestAsRequester)
                .executes(PeekRequestCommands::cancelRequest);
    }
    
    public static LiteralArgumentBuilder<CommandSourceStack> createStopCommand() {
        return Commands.literal("stop")
                .requires(source -> ValidationUtils.requiresActivePeekerWithPermission(source, Permissions.Command.STOP))
                .executes(PeekRequestCommands::stopPeek);
    }
    
    public static LiteralArgumentBuilder<CommandSourceStack> createWhoCommand() {
        return Commands.literal("who")
                .requires(ValidationUtils::requiresBeingPeeked)
                .executes(PeekRequestCommands::showWhoPeeking);
    }
    
    public static LiteralArgumentBuilder<CommandSourceStack> createCancelPlayerCommand() {
        return Commands.literal("cancel-player")
                .requires(ValidationUtils::requiresBeingPeeked)
                .then(Commands.argument("peeker", EntityArgument.player())
                    .suggests(PeekSuggestions.CANCEL_PLAYER_SUGGESTIONS)
                    .executes(PeekRequestCommands::cancelSpecificPeek));
    }
    
    private static int sendPeekRequest(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            ServerPlayer target = CommandUtils.getPlayerArgument(context, "target");
            if (!ValidationUtils.validatePlayerNotNull(target, player) || target == null) return 0;

            // Check specifically for pending request conflicts first (for better error messages)
            PeekRequestManager requestManager = ManagerRegistry.getInstance().getManager(PeekRequestManager.class);
            PeekRequest existingRequest = requestManager.getPendingRequestBetween(player.getUUID(), target.getUUID());
            if (existingRequest != null) {
                long remainingTime = existingRequest.getRemainingSeconds();
                Component message = Component.literal("§e" + Component.translatable("peek.error.request_pending_wait", target.getName().getString(), remainingTime).getString());
                player.sendSystemMessage(message, false);
                return 0;
            }

            PeekConstants.Result<PeekRequest> result = requestManager.sendRequest(player, target);
            if (!ValidationUtils.validateResult(result, player)) return 0;
            
            return 1;
        });
    }
    
    private static int acceptRequest(CommandContext<CommandSourceStack> context) {
        return CommandRequestProcessor.executeRequestCommand(context, "requester", (input) -> {
            PeekRequestManager requestManager = ManagerRegistry.getInstance().getManager(PeekRequestManager.class);
            PeekConstants.Result<PeekRequest> result = requestManager.acceptRequest(input.getPlayer(), input.getRequest().getId());
            return ValidationUtils.validateResult(result, input.getPlayer());
        });
    }
    
    private static int denyRequest(CommandContext<CommandSourceStack> context) {
        return CommandRequestProcessor.executeRequestCommand(context, "requester", (input) -> {
            PeekRequestManager requestManager = ManagerRegistry.getInstance().getManager(PeekRequestManager.class);
            PeekConstants.Result<PeekRequest> result = requestManager.denyRequest(input.getPlayer(), input.getRequest().getId());
            // denyRequest returns Result, but we can still validate it for consistency
            return ValidationUtils.validateResult(result, input.getPlayer());
        });
    }
    
    private static int cancelRequest(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            // Get the player's pending request as requester
            PeekRequest request = ManagerRegistry.getInstance().getManager(PeekRequestManager.class).getPendingRequestAsRequester(player.getUUID());
            if (request == null) {
                player.sendSystemMessage(Component.literal("§c" + Component.translatable("peek.error.no_pending_request").getString()), false);
                return 0;
            }
            
            // Cancel the request
            PeekConstants.Result<PeekRequest> result = ManagerRegistry.getInstance().getManager(PeekRequestManager.class).cancelRequest(player, request.getId());
            if (!ValidationUtils.validateResult(result, player)) return 0;
            
            // Simple success confirmation for cancel operation

            if (ServerPlayerCompat.getServer(player) == null) {
                return 0;
            }

            ServerPlayer target = ServerPlayerCompat.getServer(player).getPlayerList().getPlayer(request.getTargetId());
            Component targetName = target != null ? target.getDisplayName() : Component.translatable("argument.player.unknown");
            player.sendSystemMessage(Component.translatable("peek.request_cancelled", targetName), false);
            
            return 1;
        });
    }
    
    private static int stopPeek(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            PeekConstants.Result<String> result = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).stopPeekSession(player.getUUID(), true, ServerPlayerCompat.getServer(player));
            
            if (result.isSuccess()) {
                return 1;
            } else {
                return ValidationUtils.validateResult(result, player) ? 1 : 0;
            }
        });
    }
    
    private static int showWhoPeeking(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            List<PeekSession> targetingSessions = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).getSessionsTargeting(player.getUUID());
            
            if (!ValidationUtils.validateCollectionNotEmpty(targetingSessions, 
                    () -> Component.translatable("peek.message.no_one_peeking"), player)) {
                return 1;
            }
            
            MutableComponent message = Component.translatable("peek.message.who_peeking_header", targetingSessions.size());
            
            for (PeekSession session : targetingSessions) {
                MutableComponent sessionInfo = Component.translatable("peek.command.who.session_info",
                    session.getPeekerName(), TextUtils.formatDuration(session.getDurationSeconds()));
                
                // Add cancel button
                MutableComponent cancelButton = Component.translatable("peek.command.who.cancel_button")
                    .withStyle(style -> style
                        .withClickEvent(TextEventCompat.runCommand("/peek cancel-player " + session.getPeekerName()))
                        .withHoverEvent(TextEventCompat.showText(Component.translatable("peek.message.manage.cancel.tip"))));
                
                message.append(sessionInfo.append(cancelButton));
            }
            
            player.sendSystemMessage(message, false);
            return 1;
        });
    }
    
    private static int cancelSpecificPeek(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executePlayerCommand(context, (player) -> {
            ServerPlayer peekerPlayer = CommandUtils.getPlayerArgument(context, "peeker");
            if (!ValidationUtils.validatePlayerNotNull(peekerPlayer, player) || peekerPlayer == null) return 0;

            PeekSession session = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).getSessionByPeeker(peekerPlayer.getUUID());
            if (!ValidationUtils.validateSessionRelationship(session, player.getUUID(), peekerPlayer.getDisplayName(), player)) {
                return 0;
            }
            
            PeekConstants.Result<String> result = ManagerRegistry.getInstance().getManager(PeekSessionManager.class)
                .stopPeekSession(
                    peekerPlayer.getUUID(),
                    false,
                    ServerPlayerCompat.getServer(player),
                    Component.translatable("peek.message.cancelled_by_target", player.getDisplayName())
                );
            
            if (result.isSuccess()) {
                return 1;
            } else {
                return ValidationUtils.validateResult(result, player) ? 1 : 0;
            }
        });
    }
}


