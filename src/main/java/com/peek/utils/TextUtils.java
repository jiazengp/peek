package com.peek.utils;

import com.peek.PeekMod;
import com.peek.manager.constants.GameConstants;
import com.peek.utils.compat.TextEventCompat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class TextUtils {
    public static final Text BADGE = Text.literal(PeekMod.MOD_ID).formatted(Formatting.BOLD, Formatting.ITALIC);

    private static final Style ERROR_STYLE = Style.EMPTY.withColor(Formatting.RED);
    private static final Style WARNING_STYLE = Style.EMPTY.withColor(Formatting.YELLOW);
    private static final Style SUCCESS_STYLE = Style.EMPTY.withColor(Formatting.GREEN);
    private static final Style INFO_STYLE = Style.EMPTY.withColor(Formatting.AQUA);

    public static Text error(Text msg) {
        return Text.literal("").append(msg).setStyle(ERROR_STYLE);
    }

    public static Text warning(Text msg) {
        return Text.literal("").append(msg).setStyle(WARNING_STYLE);
    }

    public static Text success(Text msg) {
        return Text.literal("").append(msg).setStyle(SUCCESS_STYLE);
    }

    public static Text info(Text msg) {
        return Text.literal("").append(msg).setStyle(INFO_STYLE);
    }
    
    // Common text building methods
    
    public static MutableText newline() {
        return Text.literal("\n");
    }
    
    public static void addStatLine(MutableText message, String label, Object value) {
        message.append(newline())
            .append(Text.literal(label + ": ").formatted(Formatting.YELLOW))
            .append(Text.literal(String.valueOf(value)).formatted(Formatting.WHITE));
    }
    
    public static void addStatLine(MutableText message, Text label, Object value) {
        message.append(newline())
            .append(label.copy().append(Text.literal(": ")).formatted(Formatting.YELLOW));
        
        if (value instanceof Text) {
            message.append(((Text) value).copy().formatted(Formatting.WHITE));
        } else {
            message.append(Text.literal(String.valueOf(value)).formatted(Formatting.WHITE));
        }
    }
    
    public static void addColoredStat(MutableText message, String label, String value, Formatting color) {
        message.append(newline())
            .append(Text.literal(label + ": ").formatted(Formatting.YELLOW))
            .append(Text.literal(value).formatted(color));
    }
    
    public static void addColoredStat(MutableText message, Text label, String value, Formatting color) {
        message.append(newline())
            .append(label.copy().append(Text.literal(": ")).formatted(Formatting.YELLOW))
            .append(Text.literal(value).formatted(color));
    }
    
    public static MutableText createPagedHeader(String key, int page) {
        MutableText header = Text.translatable(key).formatted(Formatting.GOLD, Formatting.BOLD);
        header.append(Text.literal(" (Page " + (page + 1) + ")").formatted(Formatting.GRAY));
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
    
    public static void addPaginationControls(MutableText message, int currentPage, int totalPages, String command) {
        if (totalPages <= 1) return;
        
        message.append(Text.literal("\n\n").formatted(Formatting.GRAY));
        
        // Previous button
        if (currentPage > 0) {
            MutableText prevButton = Text.literal("[« Previous]").formatted(Formatting.AQUA, Formatting.UNDERLINE)
                .styled(style -> style.withClickEvent(TextEventCompat.runCommand("/" + command + " " + (currentPage - 1))));
            message.append(prevButton).append(Text.literal(" ").formatted(Formatting.GRAY));
        }
        
        // Page info
        message.append(Text.literal("Page " + (currentPage + 1) + "/" + totalPages).formatted(Formatting.YELLOW));
        
        // Next button
        if (currentPage < totalPages - 1) {
            MutableText nextButton = Text.literal(" [Next »]").formatted(Formatting.AQUA, Formatting.UNDERLINE)
                .styled(style -> style.withClickEvent(TextEventCompat.runCommand("/" + command + " " + (currentPage + 1))));
            message.append(nextButton);
        }
    }
    
    // Entry builders
    
    public static MutableText createRankEntry(int rank, String name, String stat, String duration) {
        return newline().append(Text.literal(rank + ". ").formatted(Formatting.YELLOW))
            .append(Text.literal(name).formatted(Formatting.AQUA))
            .append(Text.literal(" - " + stat).formatted(Formatting.WHITE))
            .append(Text.literal(" (" + duration + ")").formatted(Formatting.GRAY));
    }
    
    public static MutableText createListEntry(int index, String name, String... stats) {
        MutableText entry = newline().append(Text.literal(index + ". ").formatted(Formatting.YELLOW))
            .append(Text.literal(name).formatted(Formatting.AQUA));
        
        Formatting[] colors = {Formatting.GREEN, Formatting.LIGHT_PURPLE, Formatting.YELLOW};
        for (int i = 0; i < stats.length && i < colors.length; i++) {
            entry.append(Text.literal(" " + stats[i]).formatted(colors[i]));
        }
        
        return entry;
    }
    
    public static MutableText createSessionEntry(int index, String peekerName, String targetName, 
                                               String duration, boolean crossDimension, String stopCommand) {
        MutableText entry = newline().append(Text.literal(index + ". ").formatted(Formatting.YELLOW))
            .append(Text.literal(peekerName).formatted(Formatting.AQUA))
            .append(Text.literal(" → ").formatted(Formatting.GRAY))
            .append(Text.literal(targetName).formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal(" (" + duration + ")").formatted(Formatting.WHITE));
        
        if (crossDimension) {
            entry.append(Text.literal(" [Cross-Dim]").formatted(Formatting.RED));
        }
        
        // Add stop button
        if (stopCommand != null) {
            MutableText stopButton = Text.literal(" [Stop]").formatted(Formatting.RED, Formatting.UNDERLINE)
                .styled(style -> style
                    .withClickEvent(TextEventCompat.runCommand(stopCommand))
                    .withHoverEvent(TextEventCompat.showText("Click to force stop this session")));
            entry.append(stopButton);
        }
        
        return entry;
    }
}
