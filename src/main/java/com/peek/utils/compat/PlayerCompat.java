package com.peek.utils.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.GameType;

/**
 * Compatibility layer for ServerPlayer methods across different Minecraft versions.
 * Handles API differences between 1.21.4 and 1.21.5+.
 */
public class PlayerCompat {
    
    /**
     * Gets the ServerLevel the player is currently in.
     * Uses the appropriate method for each version.
     */
    public static ServerLevel getServerWorld(ServerPlayer player) {
        return ServerPlayerCompat.getWorld(player);
    }
    
    /**
     * Gets the player's current game mode.
     * Uses the appropriate method for each version.
     */
    public static GameType getGameMode(ServerPlayer player) {
        #if MC_VER >= 1219
        return player.gameMode();
        #elif MC_VER >= 1215
        return player.getGameMode();
        #else
        return player.interactionManager.getGameMode();
        #endif
    }
    
    /**
     * Teleports a player to the specified location with proper API compatibility.
     * Handles different teleport method signatures across versions.
     */
    public static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, 
                               float yaw, float pitch) {
        #if MC_VER >= 1219
        player.teleportTo(world, x, y, z, java.util.Set.of(), yaw, pitch, true);
        #elif MC_VER >= 1212
        // 1.21.2+ uses 8-parameter version with Set and boolean
        player.teleport(world, x, y, z, java.util.Set.of(), yaw, pitch, true);
        #else
        // 1.21.1 and earlier use 6-parameter version without Set and boolean
        player.teleport(world, x, y, z, yaw, pitch);
        #endif
    }
    
    /**
     * Safely gets the RegistryAccess from a player's server.
     * Returns null if server or registry manager is not available.
     */
    public static RegistryAccess getRegistryManager(ServerPlayer player) {
        if (player == null) {
            return null;
        }

        MinecraftServer server = ServerPlayerCompat.getServer(player);
        if (server == null) {
            return null;
        }

        #if MC_VER >= 1219
        return server.registryAccess();
        #else
        return server.getRegistryManager();
        #endif
    }
    
    /**
     * Safely gets the RegistryAccess from a server.
     * Returns null if server or registry manager is not available.
     */
    public static RegistryAccess getRegistryManager(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        
        #if MC_VER >= 1219
        return server.registryAccess();
        #else
        return server.getRegistryManager();
        #endif
    }
}



