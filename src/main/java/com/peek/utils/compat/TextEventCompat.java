package com.peek.utils.compat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.net.URI;

/**
 * Compatibility layer for Component Events across different Minecraft versions.
 * Handles API differences between 1.21.4 and 1.21.5+.
 */
public class TextEventCompat {
    
    /**
     * Creates a run command click event.
     */
    public static ClickEvent runCommand(String command) {
        // MC 1.21.4 uses ClickEvent.Action.RUN_COMMAND constructor
        // MC 1.21.5+ uses ClickEvent.RunCommand nested class
        #if MC_VER >= 1215
        return new ClickEvent.RunCommand(command);
        #else
        return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        #endif
    }
    
    /**
     * Creates a run full command click event (with / prefix).
     */
    public static ClickEvent runFullCommand(String command) {
        String fullCommand = command.startsWith("/") ? command : "/" + command;
        return runCommand(fullCommand);
    }
    
    /**
     * Creates a suggest command click event.
     */
    public static ClickEvent suggestCommand(String command) {
        #if MC_VER >= 1215
        return new ClickEvent.SuggestCommand(command);
        #else
        return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);
        #endif
    }
    
    /**
     * Creates an open URL click event.
     */
    public static ClickEvent openUrl(String url) {
        #if MC_VER >= 1215
        return new ClickEvent.OpenUrl(URI.create(url));
        #else
        return new ClickEvent(ClickEvent.Action.OPEN_URL, url);
        #endif
    }
    
    /**
     * Creates an open URL click event from URI.
     */
    public static ClickEvent openUrl(URI uri) {
        #if MC_VER >= 1215
        return new ClickEvent.OpenUrl(uri);
        #else
        return new ClickEvent(ClickEvent.Action.OPEN_URL, uri.toString());
        #endif
    }
    
    /**
     * Creates a copy to clipboard click event.
     */
    public static ClickEvent copyToClipboard(String text) {
        #if MC_VER >= 1215
        return new ClickEvent.CopyToClipboard(text);
        #else
        return new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text);
        #endif
    }
    
    /**
     * Creates a show text hover event.
     */
    public static HoverEvent showText(Component text) {
        #if MC_VER >= 1215
        return new HoverEvent.ShowText(text);
        #else
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
        #endif
    }
    
    /**
     * Creates a show text hover event from string.
     */
    public static HoverEvent showText(String text) {
        return showText(Component.literal(text));
    }
    
    /**
     * Creates a show item hover event.
     */
    public static HoverEvent showItem(ItemStack itemStack) {
        #if MC_VER >= 1215
        return new HoverEvent.ShowItem(net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(itemStack));
        #else
        return new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackContent(itemStack));
        #endif
    }
}


