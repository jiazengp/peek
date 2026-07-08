package com.peek.utils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.function.BiConsumer;

/**
 * Utility class for common command message patterns
 */
public class CommandMessageUtils {
    
    /**
     * Create a formatted header text using translation key
     */
    public static Component createHeader(String translationKey, Object... args) {
        return Component.translatable(translationKey, args).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }
    
    /**
     * Create a separator line using translation key
     */
    public static Component createSeparator(String translationKey) {
        return Component.translatable(translationKey).withStyle(ChatFormatting.GOLD);
    }
    
    /**
     * Create an info line with key-value pair
     */
    public static BiConsumer<String, String> createInfoLineSender(CommandSourceStack source) {
        return (translationKey, value) -> {
            MutableComponent line = Component.translatable(translationKey, value).withStyle(ChatFormatting.WHITE);
            source.sendSystemMessage(line);
        };
    }
    
    /**
     * Create an info line with URL handling
     */
    public static BiConsumer<String, String> createUrlInfoLineSender(CommandSourceStack source) {
        return (translationKey, value) -> {
            if (value.startsWith("https://")) {
                MutableComponent line = Component.translatable(translationKey, "")
                        .append(Component.literal(value.replaceAll("https://", ""))
                                .withStyle(style -> style.withClickEvent(TextEventFactory.openUrl(value))
                                        .withColor(ChatFormatting.AQUA)));
                source.sendSystemMessage(line);
            } else {
                MutableComponent line = Component.translatable(translationKey, value).withStyle(ChatFormatting.WHITE);
                source.sendSystemMessage(line);
            }
        };
    }
    
    /**
     * Create a debug info line
     */
    public static MutableComponent createDebugLine(String label, Object value) {
        return Component.literal("\n" + label + ": " + value).withStyle(ChatFormatting.WHITE);
    }
    
    /**
     * Create a section header for debug output
     */
    public static MutableComponent createDebugSection(String sectionName) {
        return Component.literal("\n--- " + sectionName + " ---").withStyle(ChatFormatting.GOLD);
    }
    
    /**
     * Create a simple info message
     */
    public static Component createInfo(String translationKey, Object... args) {
        return Component.translatable(translationKey, args).withStyle(ChatFormatting.GRAY);
    }
    
    /**
     * Create an error message
     */
    public static Component createError(String translationKey, Object... args) {
        return Component.translatable(translationKey, args).withStyle(ChatFormatting.RED);
    }
    
    /**
     * Create a success message
     */
    public static Component createSuccess(String translationKey, Object... args) {
        return Component.translatable(translationKey, args).withStyle(ChatFormatting.GREEN);
    }
    
    /**
     * Create a warning message
     */
    public static Component createWarning(String translationKey, Object... args) {
        return Component.translatable(translationKey, args).withStyle(ChatFormatting.YELLOW);
    }
}

