package com.peek.utils.compat;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.UserCache;
#if MC_VER >= 1219
import net.minecraft.server.PlayerConfigEntry;
#endif

import java.util.Optional;
import java.util.UUID;

/**
 * Compatibility layer for UserCache APIs across different Minecraft versions.
 * Handles API differences in 1.21.9+.
 */
public class UserCacheCompat {

    /**
     * Gets the UserCache from MinecraftServer.
     * In 1.21.9+, getUserCache() was removed, use getApiServices().nameToIdCache() instead.
     */
    public static UserCache getUserCache(MinecraftServer server) {
        #if MC_VER >= 1219
        return (UserCache) server.getApiServices().nameToIdCache();
        #else
        return server.getUserCache();
        #endif
    }

    /**
     * Gets the UserCache from a ServerPlayerEntity.
     * In 1.21.9+, getUserCache() was removed, use getApiServices().nameToIdCache() instead.
     */
    public static UserCache getUserCache(ServerPlayerEntity player) {
        #if MC_VER >= 1219
        return (UserCache) player.getEntityWorld().getServer().getApiServices().nameToIdCache();
        #else
        return player.getServer().getUserCache();
        #endif
    }

    /**
     * Gets player name by UUID from UserCache.
     * In 1.21.9+, the return type changed from Optional<GameProfile> to Optional<PlayerConfigEntry>.
     */
    public static Optional<String> getNameByUuid(UserCache userCache, UUID uuid) {
        if (userCache == null) {
            return Optional.empty();
        }
        #if MC_VER >= 1219
        return userCache.getByUuid(uuid).map(PlayerConfigEntry::name);
        #else
        return userCache.getByUuid(uuid).map(GameProfile::getName);
        #endif
    }
}
