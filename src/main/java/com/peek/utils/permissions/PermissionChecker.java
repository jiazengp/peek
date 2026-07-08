package com.peek.utils.permissions;

import com.peek.PeekMod;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public class PermissionChecker {
    public static boolean hasPermission(CommandSourceStack ctx, String permission, int fallbackLevel) {
        return Permissions.check(ctx, buildFullPermission(permission), hasFallbackPermission(ctx.permissions(), fallbackLevel));
    }

    public static boolean hasPermission(ServerPlayer player, String permission, int fallbackLevel) {
        return Permissions.check(player, buildFullPermission(permission), hasFallbackPermission(player.permissions(), fallbackLevel));
    }

    public static boolean isOp(CommandSourceStack ctx) {
        return hasPermission(ctx, com.peek.utils.permissions.Permissions.ADMIN, 4);
    }

    public static boolean isOp(ServerPlayer player) {
        return hasPermission(player, com.peek.utils.permissions.Permissions.ADMIN, 4);
    }
    
    private static String buildFullPermission(String permission) {
        return PeekMod.MOD_ID.toLowerCase() + "." + permission;
    }

    private static boolean hasFallbackPermission(net.minecraft.server.permissions.PermissionSet permissionSet, int fallbackLevel) {
        Permission fallbackPermission = new Permission.HasCommandLevel(PermissionLevel.byId(fallbackLevel));
        return permissionSet.hasPermission(fallbackPermission);
    }
}


