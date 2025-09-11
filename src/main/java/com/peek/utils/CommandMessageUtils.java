package com.peek.utils;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.BiConsumer;

/**
 * Utility class for common command message patterns
 */
public class CommandMessageUtils {
    
    /**
     * Create a formatted header text using translation key
     */
    public static Text createHeader(String translationKey, Object... args) {
        return Text.translatable(translationKey, args).formatted(Formatting.GOLD, Formatting.BOLD);
    }
    
    /**
     * Create a separator line using translation key
     */
    public static Text createSeparator(String translationKey) {
        return Text.translatable(translationKey).formatted(Formatting.GOLD);
    }
    
    /**
     * Create an info line with key-value pair
     */
    public static BiConsumer<String, String> createInfoLineSender(ServerCommandSource source) {
        return (translationKey, value) -> {
            MutableText line = Text.translatable(translationKey, value).formatted(Formatting.WHITE);
            source.sendMessage(line);
        };
    }
    
    /**
     * Create an info line with URL handling
     */
    public static BiConsumer<String, String> createUrlInfoLineSender(ServerCommandSource source) {
        return (translationKey, value) -> {
            if (value.startsWith("https://")) {
                MutableText line = Text.translatable(translationKey, "")
                        .append(Text.literal(value.replaceAll("https://", ""))
                                .styled(style -> style.withClickEvent(TextEventFactory.openUrl(value))
                                        .withColor(Formatting.AQUA)));
                source.sendMessage(line);
            } else {
                MutableText line = Text.translatable(translationKey, value).formatted(Formatting.WHITE);
                source.sendMessage(line);
            }
        };
    }
    
    /**
     * Create a debug info line
     */
    public static MutableText createDebugLine(String label, Object value) {
        return Text.literal("\n" + label + ": " + value).formatted(Formatting.WHITE);
    }
    
    /**
     * Create a section header for debug output
     */
    public static MutableText createDebugSection(String sectionName) {
        return Text.literal("\n--- " + sectionName + " ---").formatted(Formatting.GOLD);
    }
    
    /**
     * Create a simple info message
     */
    public static Text createInfo(String translationKey, Object... args) {
        return Text.translatable(translationKey, args).formatted(Formatting.GRAY);
    }
    
    /**
     * Create an error message
     */
    public static Text createError(String translationKey, Object... args) {
        return Text.translatable(translationKey, args).formatted(Formatting.RED);
    }
    
    /**
     * Create a success message
     */
    public static Text createSuccess(String translationKey, Object... args) {
        return Text.translatable(translationKey, args).formatted(Formatting.GREEN);
    }
    
    /**
     * Create a warning message
     */
    public static Text createWarning(String translationKey, Object... args) {
        return Text.translatable(translationKey, args).formatted(Formatting.YELLOW);
    }
}