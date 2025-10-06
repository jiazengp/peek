package com.peek.utils.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Compatibility layer for ServerPlayerEntity APIs across different Minecraft versions.
 * Handles API differences in 1.21.9+.
 */
public class ServerPlayerCompat {

    /**
     * Gets the MinecraftServer from a ServerPlayerEntity.
     * In 1.21.9+, getServer() was removed, use getEntityWorld().getServer() instead.
     */
    public static MinecraftServer getServer(ServerPlayerEntity player) {
        #if MC_VER >= 1219
        return player.getEntityWorld().getServer();
        #else
        return player.getServer();
        #endif
    }

    /**
     * Gets the ServerWorld from a ServerPlayerEntity.
     * In 1.21.9+, getWorld() was replaced with getEntityWorld().
     */
    public static ServerWorld getWorld(ServerPlayerEntity player) {
        #if MC_VER >= 1219
        return player.getEntityWorld();
        #else
        return (ServerWorld) player.getWorld();
        #endif
    }

    /**
     * Gets the position of a ServerPlayerEntity.
     * In 1.21.9+, getPos() was replaced with getEntityPos().
     */
    public static Vec3d getPos(ServerPlayerEntity player) {
        #if MC_VER >= 1219
        return player.getEntityPos();
        #else
        return player.getPos();
        #endif
    }
}
