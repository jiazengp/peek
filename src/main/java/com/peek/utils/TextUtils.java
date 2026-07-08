package com.peek.utils;

import com.peek.PeekMod;
import com.peek.manager.constants.GameConstants;
import com.peek.utils.compat.TextEventCompat;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class TextUtils {
    public static final Component BADGE = Component.literal(PeekMod.MOD_ID).withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC);

    private static final Style ERROR_STYLE = Style.EMPTY.withColor(ChatFormatting.RED);
    private static final Style WARNING_STYLE = Style.EMPTY.withColor(ChatFormatting.YELLOW);
    private static final Style SUCCESS_STYLE = Style.EMPTY.withColor(ChatFormatting.GREEN);
    private static final Style INFO_STYLE = Style.EMPTY.withColor(ChatFormatting.AQUA);

    public static Component error(Component msg) {
        return Component.literal("").append(msg).setStyle(ERROR_STYLE);
    }

    public static Component warning(Component msg) {
        return Component.literal("").append(msg).setStyle(WARNING_STYLE);
    }

    public static Component success(Component msg) {
        return Component.literal("").append(msg).setStyle(SUCCESS_STYLE);
    }

    public static Component info(Component msg) {
        return Component.literal("").append(msg).setStyle(INFO_STYLE);
    }
    
    // Common text building methods
    
    public static MutableComponent newline() {
        return Component.literal("\n");
    }
    
    public static void addStatLine(MutableComponent message, String label, Object value) {
        message.append(newline())
            .append(Component.literal(label + ": ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE));
    }
    
    public static void addStatLine(MutableComponent message, Component label, Object value) {
        message.append(newline())
            .append(label.copy().append(Component.literal(": ")).withStyle(ChatFormatting.YELLOW));
        
        if (value instanceof Component) {
            message.append(((Component) value).copy().withStyle(ChatFormatting.WHITE));
        } else {
            message.append(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE));
        }
    }
    
    public static void addColoredStat(MutableComponent message, String label, String value, ChatFormatting color) {
        message.append(newline())
            .append(Component.literal(label + ": ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(value).withStyle(color));
    }
    
    public static void addColoredStat(MutableComponent message, Component label, String value, ChatFormatting color) {
        message.append(newline())
            .append(label.copy().append(Component.literal(": ")).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(value).withStyle(color));
    }
    
    public static MutableComponent createPagedHeader(String key, int page) {
        MutableComponent header = Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        header.append(Component.literal(" (Page " + (page + 1) + ")").withStyle(ChatFormatting.GRAY));
        return header;
    }
    
    // Duration formatting
    
    public static String formatDuration(long seconds) {
        if (seconds < GameConstants.SECONDS_PER_MINUTE) {
            return seconds + "s";
        } else if (seconds < GameConstants.SECONDS_PER_HOUR) {
            long minutes = seconds / GameConstants.SECONDS_PER_MINUTE;
            long remainingSeconds = seconds % GameConstants.SECONDS_PER_MINUTE;
            return minutes + "m " + remainingSeconds + "s";
        } else {
            long hours = seconds / GameConstants.SECONDS_PER_HOUR;
            long minutes = (seconds % GameConstants.SECONDS_PER_HOUR) / GameConstants.SECONDS_PER_MINUTE;
            return hours + "h " + minutes + "m";
        }
    }
    
    // Pagination controls
    
    public static void addPaginationControls(MutableComponent message, int currentPage, int totalPages, String command) {
        if (totalPages <= 1) return;
        
        message.append(Component.literal("\n\n").withStyle(ChatFormatting.GRAY));
        
        // Previous button
        if (currentPage > 0) {
            MutableComponent prevButton = Component.literal("[« Previous]").withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(TextEventCompat.runCommand("/" + command + " " + (currentPage - 1))));
            message.append(prevButton).append(Component.literal(" ").withStyle(ChatFormatting.GRAY));
        }
        
        // Page info
        message.append(Component.literal("Page " + (currentPage + 1) + "/" + totalPages).withStyle(ChatFormatting.YELLOW));
        
        // Next button
        if (currentPage < totalPages - 1) {
            MutableComponent nextButton = Component.literal(" [Next »]").withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(TextEventCompat.runCommand("/" + command + " " + (currentPage + 1))));
            message.append(nextButton);
        }
    }
    
    // Entry builders
    
    public static MutableComponent createRankEntry(int rank, String name, String stat, String duration) {
        return newline().append(Component.literal(rank + ". ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(name).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" - " + stat).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" (" + duration + ")").withStyle(ChatFormatting.GRAY));
    }
    
    public static MutableComponent createListEntry(int index, String name, String... stats) {
        MutableComponent entry = newline().append(Component.literal(index + ". ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(name).withStyle(ChatFormatting.AQUA));
        
        ChatFormatting[] colors = {ChatFormatting.GREEN, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW};
        for (int i = 0; i < stats.length && i < colors.length; i++) {
            entry.append(Component.literal(" " + stats[i]).withStyle(colors[i]));
        }
        
        return entry;
    }
    
    public static MutableComponent createSessionEntry(int index, String peekerName, String targetName, 
                                               String duration, boolean crossDimension, String stopCommand) {
        MutableComponent entry = newline().append(Component.literal(index + ". ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(peekerName).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(targetName).withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal(" (" + duration + ")").withStyle(ChatFormatting.WHITE));
        
        if (crossDimension) {
            entry.append(Component.literal(" [Cross-Dim]").withStyle(ChatFormatting.RED));
        }
        
        // Add stop button
        if (stopCommand != null) {
            MutableComponent stopButton = Component.literal(" [Stop]").withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                    .withClickEvent(TextEventCompat.runCommand(stopCommand))
                    .withHoverEvent(TextEventCompat.showText("Click to force stop this session")));
            entry.append(stopButton);
        }
        
        return entry;
    }
}


