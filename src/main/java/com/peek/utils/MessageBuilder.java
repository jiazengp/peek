package com.peek.utils;

import com.peek.manager.constants.ErrorCodes;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/**
 * Utility class for building consistent translatable messages
 */
public final class MessageBuilder {
    
    private MessageBuilder() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Creates a success message with green formatting
     */
    public static Component success(String translationKey, Object... args) {
        return Component.translatable(translationKey, args).withStyle(ChatFormatting.GREEN);
    }
    
    /**
     * Creates an error message with red formatting
     */
    public static Component error(String translationKey, Object... args) {
        return Component.translatable(translationKey, args).withStyle(ChatFormatting.RED);
    }
    
    /**
     * Creates a warning message with yellow formatting
     */
    public static Component warning(String translationKey, Object... args) {
        return Component.translatable(translationKey, args).withStyle(ChatFormatting.YELLOW);
    }
    
    /**
     * Creates an info message with gray formatting
     */
    public static Component info(String translationKey, Object... args) {
        return Component.translatable(translationKey, args).withStyle(ChatFormatting.GRAY);
    }
    
    /**
     * Creates an aqua-colored message for special notifications
     */
    public static Component special(String translationKey, Object... args) {
        return Component.translatable(translationKey, args).withStyle(ChatFormatting.AQUA);
    }
    
    /**
     * Creates a translatable message (formatting is handled in lang file)
     */
    public static Component message(String translationKey, Object... args) {
        return Component.translatable(translationKey, args);
    }
    
    /**
     * Sends a message to player with optional overlay
     */
    public static void sendMessage(net.minecraft.server.level.ServerPlayer player, String translationKey, boolean overlay, Object... args) {
        player.sendSystemMessage(Component.translatable(translationKey, args), overlay);
    }
    
    /**
     * Sends a regular chat message
     */
    public static void sendChat(net.minecraft.server.level.ServerPlayer player, String translationKey, Object... args) {
        sendMessage(player, translationKey, false, args);
    }
    
    /**
     * Sends an overlay message (appears above hotbar)
     */
    public static void sendOverlay(net.minecraft.server.level.ServerPlayer player, String translationKey, Object... args) {
        sendMessage(player, translationKey, true, args);
    }
    
    /**
     * Creates an error message from ErrorCodes enum
     */
    public static Component error(ErrorCodes errorCode, Object... args) {
        return error(errorCode.getTranslationKey(), args);
    }
    
    /**
     * Creates a clickable button with hover text
     */
    public static MutableComponent button(String translationKey, ChatFormatting color, 
                                    net.minecraft.network.chat.ClickEvent clickEvent, 
                                    net.minecraft.network.chat.HoverEvent hoverEvent) {
        return Component.translatable(translationKey)
            .withStyle(color, ChatFormatting.UNDERLINE)
            .withStyle(style -> style
                .withClickEvent(clickEvent)
                .withHoverEvent(hoverEvent)
            );
    }
    
    /**
     * Creates a bracketed button (e.g., " [Accept]")
     */
    public static MutableComponent bracketedButton(String translationKey, ChatFormatting color, 
                                            net.minecraft.network.chat.ClickEvent clickEvent, 
                                            net.minecraft.network.chat.HoverEvent hoverEvent) {
        return Component.literal(" [")
            .append(Component.translatable(translationKey))
            .append("]")
            .withStyle(color, ChatFormatting.UNDERLINE)
            .withStyle(style -> style
                .withClickEvent(clickEvent)
                .withHoverEvent(hoverEvent)
            );
    }
}

