package com.peek.config;

import com.peek.PeekMod;
import de.exlll.configlib.YamlConfigurations;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfigManager {
    private static volatile ModConfig CONFIG;
    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(PeekMod.MOD_ID);
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("config.yml");

    public static void loadConfig() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                Files.createDirectories(CONFIG_DIR);
                CONFIG = new ModConfig();
                saveConfig();
            } else {
                CONFIG = YamlConfigurations.load(CONFIG_PATH, ModConfig.class);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load or create config file", e);
        }
    }

    public static void reloadConfig() {
        loadConfig();
    }

    public static void saveConfig() {
        if (CONFIG != null) {
            YamlConfigurations.save(CONFIG_PATH, ModConfig.class, CONFIG);
        }
    }

    public static ModConfig getConfig() {
        if (CONFIG == null) {
            loadConfig();
        }
        return CONFIG;
    }

    // Peek configuration getters
    public static int getRequestTimeoutSeconds() {
        return getConfig().peek.requestTimeoutSeconds;
    }
    
    public static int getCooldownSeconds() {
        return getConfig().peek.cooldownSeconds;
    }
    
    public static int getStaticRequiredTicks() {
        return getConfig().peek.staticRequiredTicks;
    }
    
    public static double getMaxDistance() {
        return getConfig().peek.maxDistance;
    }
    
    public static boolean isAllowCrossDimension() {
        return getConfig().peek.allowCrossDimension;
    }
    
    public static boolean isAllowDimensionFollowing() {
        return getConfig().peek.allowDimensionFollowing;
    }
    
    public static int getDimensionFollowDelayTicks() {
        return getConfig().peek.dimensionFollowDelayTicks;
    }
    
    public static long getMaxSessionDuration() {
        return getConfig().peek.maxSessionDuration;
    }
    
    public static double getDistanceCheckInterval() {
        return getConfig().peek.distanceCheckInterval;
    }
    
    
    // Performance configuration getters
    public static int getMaxActiveSessions() {
        return getConfig().performance.maxActiveSessions;
    }
    
    public static int getStatsCacheTtlMinutes() {
        return getConfig().performance.statsCacheTtlMinutes;
    }
    
    public static long getCleanupIntervalSeconds() {
        return getConfig().performance.cleanupIntervalSeconds;
    }
    
    public static int getMaxConcurrentRequestsPerPlayer() {
        return getConfig().performance.maxConcurrentRequestsPerPlayer;
    }
    
    public static long getSessionUpdateIntervalTicks() {
        return getConfig().performance.sessionUpdateIntervalTicks;
    }
    
    public static double getMaxPeekMoveDistance() {
        return getConfig().peek.maxPeekMoveDistance;
    }
    
    public static boolean shouldTeleportBackOnDistanceExceeded() {
        return "TELEPORT_BACK".equalsIgnoreCase(getConfig().peek.moveDistanceAction);
    }
    
    public static boolean shouldEndPeekOnDistanceExceeded() {
        return "END_PEEK".equalsIgnoreCase(getConfig().peek.moveDistanceAction);
    }
    
    public static int getAutoAcceptDelaySeconds() {
        return getConfig().peek.autoAcceptDelaySeconds;
    }
    
    public static int getInviteCooldownSeconds() {
        return getConfig().peek.inviteCooldownSeconds;
    }
    
    public static int getMaxInviteCount() {
        return getConfig().peek.maxInviteCount;
    }
    
    public static int getInviteExpirationSeconds() {
        return getConfig().peek.inviteExpirationSeconds;
    }
    
    public static int getMaxPeekSessionsPerPlayer() {
        return getConfig().peek.maxPeekSessionsPerPlayer;
    }
    
    public static double getNoMobsRadius() {
        return getConfig().peek.noMobsRadius;
    }
    
    public static double getMinSameDimensionDistance() {
        return getConfig().peek.minSameDimensionDistance;
    }
    
    // UI configuration getters
    public static int getDefaultPageSize() {
        return getConfig().ui.defaultPageSize;
    }
    
    public static int getMaxPageSize() {
        return getConfig().ui.maxPageSize;
    }
    
    public static boolean isShowDetailedStats() {
        return getConfig().ui.showDetailedStats;
    }
    
    public static boolean isShowDimensionIndicator() {
        return getConfig().ui.showDimensionIndicator;
    }
    
    // Sound configuration getters
    public static boolean isSoundsEnabled() {
        return getConfig().sounds.enableSounds;
    }
    
    // Request sounds
    public static String getRequestReceivedSound() {
        return getConfig().sounds.requestReceivedSound;
    }
    
    public static float getRequestReceivedVolume() {
        return getConfig().sounds.requestReceivedVolume;
    }
    
    public static float getRequestReceivedPitch() {
        return getConfig().sounds.requestReceivedPitch;
    }
    
    // Invite sounds
    public static String getInviteReceivedSound() {
        return getConfig().sounds.inviteReceivedSound;
    }
    
    public static float getInviteReceivedVolume() {
        return getConfig().sounds.inviteReceivedVolume;
    }
    
    public static float getInviteReceivedPitch() {
        return getConfig().sounds.inviteReceivedPitch;
    }
    
    // Teleport sounds
    public static String getTeleportToTargetSound() {
        return getConfig().sounds.teleportToTargetSound;
    }
    
    public static float getTeleportVolume() {
        return getConfig().sounds.teleportVolume;
    }
    
    public static float getTeleportPitch() {
        return getConfig().sounds.teleportPitch;
    }
    
    // Restore teleport sounds
    public static String getTeleportBackSound() {
        return getConfig().sounds.teleportBackSound;
    }
    
    public static float getTeleportBackVolume() {
        return getConfig().sounds.teleportBackVolume;
    }
    
    public static float getTeleportBackPitch() {
        return getConfig().sounds.teleportBackPitch;
    }
    
    // Being peeked sounds
    public static String getBeingPeekedSound() {
        return getConfig().sounds.beingPeekedSound;
    }
    
    public static float getBeingPeekedVolume() {
        return getConfig().sounds.beingPeekedVolume;
    }
    
    public static float getBeingPeekedPitch() {
        return getConfig().sounds.beingPeekedPitch;
    }
    
    // Session end sounds
    public static String getSessionEndSound() {
        return getConfig().sounds.sessionEndSound;
    }
    
    public static float getSessionEndVolume() {
        return getConfig().sounds.sessionEndVolume;
    }
    
    public static float getSessionEndPitch() {
        return getConfig().sounds.sessionEndPitch;
    }
    
    // Particle effects configuration getters
    public static boolean isParticleEffectsEnabled() {
        return getConfig().particles.enableParticleEffects;
    }
    
    public static String getParticleType() {
        return getConfig().particles.particleType;
    }
    
    public static int getParticleFrequencyTicks() {
        return getConfig().particles.particleFrequencyTicks;
    }
    
    public static int getParticlesPerSpawn() {
        return getConfig().particles.particlesPerSpawn;
    }
    
    public static double getParticleSpread() {
        return getConfig().particles.particleSpread;
    }
    
    public static double getParticleVelocity() {
        return getConfig().particles.particleVelocity;
    }
    
    public static boolean isParticleOnlyVisibleToTarget() {
        return getConfig().particles.onlyVisibleToTarget;
    }
    
    public static double getParticleMaxViewDistance() {
        return getConfig().particles.maxViewDistance;
    }
    
    public static String getDustParticleColor() {
        return getConfig().particles.dustParticleColor;
    }
    
    // Legacy compatibility
    @Deprecated
    public static boolean isEnableSoundNotifications() {
        return isSoundsEnabled();
    }
    
    // Debug and logging configuration methods
    public static boolean isDetailedLoggingEnabled() {
        return false; // Default to false, can be made configurable later
    }
    
    public static boolean isRequestMetricsEnabled() {
        return false; // Default to false
    }
    
    public static boolean isSessionMetricsEnabled() {
        return false; // Default to false
    }
    
    public static boolean isTeleportTrackingEnabled() {
        return false; // Default to false
    }
    
    public static boolean isDimensionTrackingEnabled() {
        return false; // Default to false
    }
    
    public static boolean isRequestEventHandlingEnabled() {
        return true; // Default to true
    }
    
    public static boolean isSessionEventHandlingEnabled() {
        return true; // Default to true
    }
    
    public static boolean isStatisticsEventHandlingEnabled() {
        return true; // Default to true
    }
    
    public static boolean isTeleportEventHandlingEnabled() {
        return true; // Default to true
    }
    
    // Statistics configuration
    public static long getStatisticsHistoryRetentionDays() {
        return 30; // Default 30 days
    }
}
