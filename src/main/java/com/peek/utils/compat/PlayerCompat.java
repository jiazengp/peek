package com.peek.utils.compat;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.world.GameMode;

/**
 * Compatibility layer for ServerPlayerEntity methods across different Minecraft versions.
 * Handles API differences between 1.21.4 and 1.21.5+.
 */
public class PlayerCompat {
    
    /**
     * Gets the ServerWorld the player is currently in.
     * Uses the appropriate method for each version.
     */
    public static ServerWorld getServerWorld(ServerPlayerEntity player) {
        return ServerPlayerCompat.getWorld(player);
    }
    
    /**
     * Gets the player's current game mode.
     * Uses the appropriate method for each version.
     */
    public static GameMode getGameMode(ServerPlayerEntity player) {
        #if MC_VER >= 1215
        return player.getGameMode();
        #else
        return player.interactionManager.getGameMode();
        #endif
    }
    
    /**
     * Teleports a player to the specified location with proper API compatibility.
     * Handles different teleport method signatures across versions.
     */
    public static void teleport(ServerPlayerEntity player, ServerWorld world, double x, double y, double z, 
                               float yaw, float pitch) {
        #if MC_VER >= 1212
        // 1.21.2+ uses 8-parameter version with Set and boolean
        player.teleport(world, x, y, z, java.util.Set.of(), yaw, pitch, true);
        #else
        // 1.21.1 and earlier use 6-parameter version without Set and boolean
        player.teleport(world, x, y, z, yaw, pitch);
        #endif
    }
    
    /**
     * Safely gets the DynamicRegistryManager from a player's server.
     * Returns null if server or registry manager is not available.
     */
    public static DynamicRegistryManager getRegistryManager(ServerPlayerEntity player) {
        if (player == null) {
            return null;
        }

        MinecraftServer server = ServerPlayerCompat.getServer(player);
        if (server == null) {
            return null;
        }

        return server.getRegistryManager();
    }
    
    /**
     * Safely gets the DynamicRegistryManager from a server.
     * Returns null if server or registry manager is not available.
     */
    public static DynamicRegistryManager getRegistryManager(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        
        return server.getRegistryManager();
    }
}