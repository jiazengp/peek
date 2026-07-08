package com.peek.utils.compat;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
#if MC_VER >= 1219
import net.minecraft.server.players.NameAndId;
#endif

import java.util.Optional;
import java.util.UUID;

/**
 * Compatibility layer for user-name lookup APIs across different Minecraft versions.
 * Handles API differences in 1.21.9+.
 */
public class UserCacheCompat {

    /**
     * Gets player name by UUID from MinecraftServer.
     */
    public static Optional<String> getNameByUuid(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) {
            return Optional.empty();
        }

		#if MC_VER >= 1219
        return server.services().nameToIdCache().get(uuid).map(NameAndId::name);
		#else
        return server.getUserCache().getByUuid(uuid).map(GameProfile::getName);
		#endif
    }

    /**
     * Gets player name by UUID from a ServerPlayer.
     */
    public static Optional<String> getNameByUuid(ServerPlayer player, UUID uuid) {
        if (player == null) {
            return Optional.empty();
        }

        return getNameByUuid(ServerPlayerCompat.getServer(player), uuid);
    }
}




