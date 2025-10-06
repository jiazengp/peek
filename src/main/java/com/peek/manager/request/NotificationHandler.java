package com.peek.manager.request;

import com.peek.config.ModConfigManager;
import com.peek.data.peek.PeekRequest;
import com.peek.data.peek.PlayerPeekData;
import com.peek.manager.constants.RequestConstants;
import com.peek.utils.MessageBuilder;
import com.peek.utils.SoundManager;
import com.peek.utils.compat.ProfileCompat;
import com.peek.utils.compat.TextEventCompat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Handles notification sending for peek requests
 */
public class NotificationHandler {
    
    /**
     * Sends request notification to both requester and target
     */
    public void sendRequestNotification(ServerPlayerEntity requester, ServerPlayerEntity target, PeekRequest request,
                                      AutoAcceptCallback autoAcceptCallback) {
        // Send to requester
        MessageBuilder.sendChat(requester, "peek.request_sent", ProfileCompat.getName(target.getGameProfile()));

        // Check if target has auto-accept enabled or requester is whitelisted
        PlayerPeekData targetData = com.peek.data.peek.PlayerPeekData.getOrCreate(target);
        boolean hasAutoAccept = targetData.autoAccept();
        boolean isWhitelisted = targetData.isWhitelisted(requester.getUuid());
        boolean shouldAutoAccept = hasAutoAccept || isWhitelisted;

        // Don't use overlay for interactive messages with buttons

        // Send request message with interactive buttons in chat
        String requesterName = ProfileCompat.getName(requester.getGameProfile());
        
        // Create the complete message with text and buttons
        MutableText requestMessage = Text.translatable("peek.request_received", requester.getDisplayName());
        
        // Add buttons on the same line
        MutableText acceptButton = MessageBuilder.bracketedButton(
            RequestConstants.ACCEPT_BUTTON_TEXT, 
            Formatting.GREEN,
            TextEventCompat.runCommand("/peek accept " + requesterName),
            TextEventCompat.showText(Text.translatable("peek.button.accept.hover", requesterName))
        );
        
        MutableText denyButton = MessageBuilder.bracketedButton(
            RequestConstants.DENY_BUTTON_TEXT,
            Formatting.RED,
            TextEventCompat.runCommand("/peek deny " + requesterName),
            TextEventCompat.showText(Text.translatable("peek.button.deny.hover", requesterName))
        );
        
        // Combine message and buttons with a space separator
        requestMessage.append(Text.literal(" ")).append(acceptButton).append(denyButton);
        target.sendMessage(requestMessage, false);
        
        // Add auto-accept information if enabled
        if (shouldAutoAccept) {
            int autoAcceptDelay = ModConfigManager.getAutoAcceptDelaySeconds();
            
            // Show different messages based on reason for auto-accept
            if (isWhitelisted && hasAutoAccept) {
                MessageBuilder.sendChat(target, "peek.message.auto_accept_whitelist_countdown", autoAcceptDelay);
            } else if (isWhitelisted) {
                MessageBuilder.sendChat(target, "peek.message.whitelist_auto_accept_countdown", autoAcceptDelay);
            } else {
                MessageBuilder.sendChat(target, "peek.message.auto_accept_countdown", autoAcceptDelay);
            }
            
            // Schedule auto-accept through callback
            autoAcceptCallback.scheduleAutoAccept(request.getId(), target, autoAcceptDelay);
        } else {
            // Show regular expiration info
            int expireTime = ModConfigManager.getRequestTimeoutSeconds();
            MessageBuilder.sendChat(target, "peek.message.request_expires", expireTime);
        }
        
        // Play sound notification
        SoundManager.playRequestReceivedSound(target);
    }
    
    /**
     * Sends notification when request is accepted
     */
    public void sendAcceptedNotifications(ServerPlayerEntity requester, ServerPlayerEntity target, PeekRequest request) {
        // Only send notification to requester - target already knows they accepted it
        if (requester != null) {
            MessageBuilder.sendChat(requester, "peek.request_accepted", request.getTargetName());
        }
    }
    
    /**
     * Sends notification when request is denied
     */
    public void sendDeniedNotifications(ServerPlayerEntity requester, ServerPlayerEntity target, PeekRequest request) {
        // Only send notification to requester - target already knows they denied it
        if (requester != null) {
            MessageBuilder.sendChat(requester, "peek.request_denied", request.getTargetName());
        }
    }
    
    /**
     * Sends notification when request is cancelled by requester
     */
    public void sendCancelledNotifications(ServerPlayerEntity requester, ServerPlayerEntity target, PeekRequest request) {
        MessageBuilder.sendChat(requester, "peek.request_cancelled", request.getTargetName());
        
        if (target != null) {
            MessageBuilder.sendChat(target, "peek.request_cancelled_by_requester", request.getRequesterName());
        }
    }
    
    /**
     * Callback interface for auto-accept scheduling
     */
    public interface AutoAcceptCallback {
        void scheduleAutoAccept(java.util.UUID requestId, ServerPlayerEntity target, int delaySeconds);
    }
}