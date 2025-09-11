package com.peek.utils;

import com.peek.utils.permissions.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player cooldowns for peek operations
 */
public class CooldownManager {
    private static final CooldownManager INSTANCE = new CooldownManager();
    
    private final Map<UUID, Long> peekCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> inviteCooldowns = new ConcurrentHashMap<>();

    private CooldownManager() {}
    
    public static CooldownManager getInstance() {
        return INSTANCE;
    }

    /**
     * Checks if a player is on peek cooldown
     */
    public boolean isOnCooldown(@NotNull UUID playerId) {
        return isOnCooldown(playerId, peekCooldowns);
    }
    
    /**
     * Checks if a player is on peek cooldown, with bypass permission check
     */
    public boolean isOnCooldown(@NotNull ServerPlayerEntity player) {
        if (ValidationUtils.canBypass(player, Permissions.Bypass.COOLDOWN, 2)) {
            return false;
        }
        return isOnCooldown(player.getUuid());
    }
    
    /**
     * Checks if a player is on invite cooldown
     */
    public boolean isOnInviteCooldown(@NotNull UUID playerId) {
        return isOnCooldown(playerId, inviteCooldowns);
    }
    
    /**
     * Checks if a player is on invite cooldown, with bypass permission check
     */
    public boolean isOnInviteCooldown(@NotNull ServerPlayerEntity player) {
        if (ValidationUtils.canBypass(player, Permissions.Bypass.COOLDOWN, 2)) {
            return false;
        }
        return isOnInviteCooldown(player.getUuid());
    }
    
    /**
     * Generic cooldown check
     */
    private boolean isOnCooldown(@NotNull UUID playerId, Map<UUID, Long> cooldownMap) {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) return false;
        
        Long cooldownEnd = cooldownMap.get(playerId);
        if (cooldownEnd == null) return false;

        return Instant.now().toEpochMilli() < cooldownEnd;
    }

    /**
     * Sets a peek cooldown for a player
     */
    public void setCooldown(@NotNull UUID playerId, int cooldownSeconds) {
        setCooldown(playerId, cooldownSeconds, peekCooldowns);
    }
    
    /**
     * Sets an invite cooldown for a player
     */
    public void setInviteCooldown(@NotNull UUID playerId, int cooldownSeconds) {
        setCooldown(playerId, cooldownSeconds, inviteCooldowns);
    }
    
    /**
     * Generic cooldown setter
     */
    private void setCooldown(@NotNull UUID playerId, int cooldownSeconds, Map<UUID, Long> cooldownMap) {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) return;

        long cooldownEnd = Instant.now().toEpochMilli() + (cooldownSeconds * 1000L);
        cooldownMap.put(playerId, cooldownEnd);
    }

    /**
     * Gets the remaining peek cooldown time for a player
     */
    public long getRemainingCooldown(@NotNull UUID playerId) {
        return getRemainingCooldown(playerId, peekCooldowns);
    }
    
    /**
     * Gets the remaining invite cooldown time for a player
     */
    public long getRemainingInviteCooldown(@NotNull UUID playerId) {
        return getRemainingCooldown(playerId, inviteCooldowns);
    }
    
    /**
     * Generic cooldown remaining getter
     */
    private long getRemainingCooldown(@NotNull UUID playerId, Map<UUID, Long> cooldownMap) {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) return 0;

        Long cooldownEnd = cooldownMap.get(playerId);
        if (cooldownEnd == null) return 0;

        long remaining = cooldownEnd - Instant.now().toEpochMilli();
        return Math.max(0, remaining / 1000); // Convert to seconds
    }

    /**
     * Clears peek cooldown for a specific player
     */
    public void clearPlayerCooldown(@NotNull UUID playerId) {
        peekCooldowns.remove(playerId);
    }
    
    /**
     * Clears invite cooldown for a specific player  
     */
    public void clearPlayerInviteCooldown(@NotNull UUID playerId) {
        inviteCooldowns.remove(playerId);
    }

    /**
     * Clears all cooldowns
     */
    public void clearAllCooldowns() {
        peekCooldowns.clear();
        inviteCooldowns.clear();
    }

    /**
     * Removes expired cooldowns for cleanup
     */
    public void cleanupExpiredCooldowns() {
        long now = Instant.now().toEpochMilli();
        peekCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        inviteCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    /**
     * Gets the total number of active cooldowns
     *
     * @return the number of active cooldowns
     */
    public int getActiveCooldownCount() {
        return peekCooldowns.size() + inviteCooldowns.size();
    }
}