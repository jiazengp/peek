package com.peek.utils.compat;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.Optional;

/**
 * Compatibility layer for Profile and GameProfile APIs across different Minecraft versions.
 * Handles API differences in 1.21.9+.
 */
public class ProfileCompat {

    /**
     * Creates a ResolvableProfile from a GameProfile.
     * In 1.21.9+, the constructor signature changed.
     */
    public static ResolvableProfile createProfileComponent(GameProfile gameProfile) {
        if (gameProfile == null) {
            throw new IllegalArgumentException("GameProfile cannot be null");
        }
        #if MC_VER >= 1219
        if (gameProfile.id() == null) {
            throw new IllegalArgumentException("GameProfile ID cannot be null");
        }
        return ResolvableProfile.createResolved(gameProfile);
        #else
        return new ResolvableProfile(gameProfile);
        #endif
    }

    /**
     * Gets the name from a GameProfile.
     * In 1.21.9+, getName() returns String instead of a property.
     */
    public static String getName(GameProfile gameProfile) {
        #if MC_VER >= 1219
        return gameProfile.name();
        #else
        return gameProfile.getName();
        #endif
    }
}


