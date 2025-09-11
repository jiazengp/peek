package com.peek.utils;

import com.peek.manager.constants.ErrorCodes;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
    public static Text success(String translationKey, Object... args) {
        return Text.translatable(translationKey, args).formatted(Formatting.GREEN);
    }
    
    /**
     * Creates an error message with red formatting
     */
    public static Text error(String translationKey, Object... args) {
        return Text.translatable(translationKey, args).formatted(Formatting.RED);
    }
    
    /**
     * Creates a warning message with yellow formatting
     */
    public static Text warning(String translationKey, Object... args) {
        return Text.translatable(translationKey, args).formatted(Formatting.YELLOW);
    }
    
    /**
     * Creates an info message with gray formatting
     */
    public static Text info(String translationKey, Object... args) {
        return Text.translatable(translationKey, args).formatted(Formatting.GRAY);
    }
    
    /**
     * Creates an aqua-colored message for special notifications
     */
    public static Text special(String translationKey, Object... args) {
        return Text.translatable(translationKey, args).formatted(Formatting.AQUA);
    }
    
    /**
     * Creates a translatable message (formatting is handled in lang file)
     */
    public static Text message(String translationKey, Object... args) {
        return Text.translatable(translationKey, args);
    }
    
    /**
     * Sends a message to player with optional overlay
     */
    public static void sendMessage(net.minecraft.server.network.ServerPlayerEntity player, String translationKey, boolean overlay, Object... args) {
        player.sendMessage(Text.translatable(translationKey, args), overlay);
    }
    
    /**
     * Sends a regular chat message
     */
    public static void sendChat(net.minecraft.server.network.ServerPlayerEntity player, String translationKey, Object... args) {
        sendMessage(player, translationKey, false, args);
    }
    
    /**
     * Sends an overlay message (appears above hotbar)
     */
    public static void sendOverlay(net.minecraft.server.network.ServerPlayerEntity player, String translationKey, Object... args) {
        sendMessage(player, translationKey, true, args);
    }
    
    /**
     * Creates an error message from ErrorCodes enum
     */
    public static Text error(ErrorCodes errorCode, Object... args) {
        return error(errorCode.getTranslationKey(), args);
    }
    
    /**
     * Creates a clickable button with hover text
     */
    public static MutableText button(String translationKey, Formatting color, 
                                    net.minecraft.text.ClickEvent clickEvent, 
                                    net.minecraft.text.HoverEvent hoverEvent) {
        return Text.translatable(translationKey)
            .formatted(color, Formatting.UNDERLINE)
            .styled(style -> style
                .withClickEvent(clickEvent)
                .withHoverEvent(hoverEvent)
            );
    }
    
    /**
     * Creates a bracketed button (e.g., " [Accept]")
     */
    public static MutableText bracketedButton(String translationKey, Formatting color, 
                                            net.minecraft.text.ClickEvent clickEvent, 
                                            net.minecraft.text.HoverEvent hoverEvent) {
        return Text.literal(" [")
            .append(Text.translatable(translationKey))
            .append("]")
            .formatted(color, Formatting.UNDERLINE)
            .styled(style -> style
                .withClickEvent(clickEvent)
                .withHoverEvent(hoverEvent)
            );
    }
}