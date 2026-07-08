package com.peek.utils.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Compatibility layer for ServerPlayer APIs across different Minecraft versions.
 * Handles API differences in 1.21.9+.
 */
public class ServerPlayerCompat {

    /**
     * Gets the MinecraftServer from a ServerPlayer.
     * In 1.21.9+, getServer() was removed, use getEntityWorld().getServer() instead.
     */
    public static MinecraftServer getServer(ServerPlayer player) {
        #if MC_VER >= 1219
        return player.level().getServer();
        #else
        return player.getServer();
        #endif
    }

    /**
     * Gets the ServerLevel from a ServerPlayer.
     * In 1.21.9+, getWorld() was replaced with getEntityWorld().
     */
    public static ServerLevel getWorld(ServerPlayer player) {
        #if MC_VER >= 1219
        return player.level();
        #else
        return (ServerLevel) player.getWorld();
        #endif
    }

    /**
     * Gets the position of a ServerPlayer.
     * In 1.21.9+, getPos() was replaced with getEntityPos().
     */
    public static Vec3 getPos(ServerPlayer player) {
        #if MC_VER >= 1219
        return player.position();
        #else
        return player.getPos();
        #endif
    }
}



