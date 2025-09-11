package com.peek.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.*;

@Configuration
public class ModConfig {
    @Comment("Peek system configuration")
    public PeekSettings peek = new PeekSettings();
    
    @Comment("Performance and memory management settings")
    public PerformanceSettings performance = new PerformanceSettings();
    
    @Comment("User interface and display settings")
    public UiSettings ui = new UiSettings();
    
    @Comment("Sound notification settings")
    public SoundSettings sounds = new SoundSettings();
    
    @Comment("Particle effects settings for peek sessions")
    public ParticleSettings particles = new ParticleSettings();

    @Configuration  
    public static class PeekSettings {
        @Comment("Request timeout in seconds")
        public int requestTimeoutSeconds = 30;
        
        @Comment("Cooldown between peek requests in seconds")
        public int cooldownSeconds = 60;
        
        @Comment("Time player must stay still before peeking (in ticks, 20 = 1 second)")
        public int staticRequiredTicks = 20;
        
        @Comment("Maximum peek distance in blocks (-1 for unlimited)")
        public double maxDistance = 100.0;
        
        @Comment("Allow cross-dimension peeking")
        public boolean allowCrossDimension = true;
        
        @Comment("Allow following target when they change dimension during peek")
        public boolean allowDimensionFollowing = true;
        
        @Comment("Delay in ticks before following target to new dimension (20 ticks = 1 second)")
        public int dimensionFollowDelayTicks = 20;
        
        @Comment("Maximum session duration in seconds (0 for unlimited)")
        public long maxSessionDuration = 3600;
        
        @Comment("Distance check interval in seconds")
        public double distanceCheckInterval = 5.0;
        
        @Comment("Maximum distance peek player can move from target (0 = unlimited)")
        public double maxPeekMoveDistance = 50.0;
        
        @Comment("Action when peek player exceeds move distance: TELEPORT_BACK or END_PEEK")
        public String moveDistanceAction = "TELEPORT_BACK";
        
        @Comment("Auto accept delay in seconds for auto-accept feature")
        public int autoAcceptDelaySeconds = 3;
        
        @Comment("Require no hostile mobs within this radius to start peek (0 = no check)")
        public double noMobsRadius = 6.0;
        
        @Comment("Minimum distance between players in same dimension to allow peek (0 = no limit)")
        public double minSameDimensionDistance = 0.0;
        
        @Comment("Cooldown between invite commands in seconds")
        public int inviteCooldownSeconds = 120;
        
        @Comment("Maximum number of players that can be invited at once")
        public int maxInviteCount = 5;
        
        @Comment("Invite link expiration time in seconds")
        public int inviteExpirationSeconds = 300;
        
        @Comment("Maximum number of peek sessions per player (0 = unlimited)")
        public int maxPeekSessionsPerPlayer = 0;
        
        
        public PeekSettings() {}
    }
    
    @Configuration
    public static class PerformanceSettings {
        @Comment("Maximum concurrent active sessions")
        public int maxActiveSessions = 100;
        
        @Comment("Statistics cache TTL in minutes")
        public int statsCacheTtlMinutes = 10;
        
        @Comment("Cleanup interval in seconds")
        public long cleanupIntervalSeconds = 60;
        
        @Comment("Maximum requests per player concurrently")
        public int maxConcurrentRequestsPerPlayer = 3;
        
        @Comment("Session update interval in ticks")
        public long sessionUpdateIntervalTicks = 20;
        
        public PerformanceSettings() {}
    }
    
    @Configuration
    public static class UiSettings {
        @Comment("Default page size for paginated results")
        public int defaultPageSize = 10;
        
        @Comment("Maximum page size allowed")
        public int maxPageSize = 50;
        
        @Comment("Show detailed statistics in admin commands")
        public boolean showDetailedStats = true;
        
        @Comment("Show cross-dimension indicator in messages")
        public boolean showDimensionIndicator = true;
        
        public UiSettings() {}
    }

    @Configuration
    public static class SoundSettings {
        @Comment("Enable sound notifications globally")
        public boolean enableSounds = true;
        
        @Comment("Sound when receiving a peek request")
        public String requestReceivedSound = "minecraft:entity.experience_orb.pickup";
        
        @Comment("Volume for request received sound (0.0 - 1.0)")
        public float requestReceivedVolume = 0.6f;
        
        @Comment("Pitch for request received sound (0.5 - 2.0)")
        public float requestReceivedPitch = 1.2f;
        
        @Comment("Sound when receiving a peek invitation")
        public String inviteReceivedSound = "minecraft:block.note_block.bell";
        
        @Comment("Volume for invite received sound (0.0 - 1.0)")
        public float inviteReceivedVolume = 0.7f;
        
        @Comment("Pitch for invite received sound (0.5 - 2.0)")
        public float inviteReceivedPitch = 1.4f;
        
        @Comment("Sound when teleporting to start peek session")
        public String teleportToTargetSound = "minecraft:entity.enderman.teleport";
        
        @Comment("Volume for teleport sound (0.0 - 1.0)")
        public float teleportVolume = 0.7f;
        
        @Comment("Pitch for teleport sound (0.5 - 2.0)")
        public float teleportPitch = 1.2f;
        
        @Comment("Sound when restoring position after peek ends")
        public String teleportBackSound = "minecraft:entity.enderman.teleport";
        
        @Comment("Volume for restore teleport sound (0.0 - 1.0)")
        public float teleportBackVolume = 0.6f;
        
        @Comment("Pitch for restore teleport sound (0.5 - 2.0)")
        public float teleportBackPitch = 0.9f;
        
        @Comment("Sound when being peeked by someone")
        public String beingPeekedSound = "minecraft:entity.player.levelup";
        
        @Comment("Volume for being peeked sound (0.0 - 1.0)")
        public float beingPeekedVolume = 0.3f;
        
        @Comment("Pitch for being peeked sound (0.5 - 2.0)")
        public float beingPeekedPitch = 1.5f;
        
        @Comment("Sound when peek session ends")
        public String sessionEndSound = "minecraft:block.note_block.chime";
        
        @Comment("Volume for session end sound (0.0 - 1.0)")
        public float sessionEndVolume = 0.4f;
        
        @Comment("Pitch for session end sound (0.5 - 2.0)")
        public float sessionEndPitch = 1.0f;
        
        public SoundSettings() {}
    }
    
    @Configuration
    public static class ParticleSettings {
        @Comment("Enable particle effects for peekers globally")
        public boolean enableParticleEffects = true;
        
        @Comment("Particle type to spawn around peekers (FLAME, PORTAL, ENCHANT, HEART, NOTE, DUST, SPARKLE)")
        public String particleType = "ENCHANT";
        
        @Comment("Frequency of particle spawning (every N ticks, 20 ticks = 1 second)")
        public int particleFrequencyTicks = 20;
        
        @Comment("Number of particles to spawn per tick when active")
        public int particlesPerSpawn = 2;
        
        @Comment("Particle spread radius around the peeker (in blocks)")
        public double particleSpread = 1.0;
        
        @Comment("Particle velocity multiplier (higher = faster particles)")
        public double particleVelocity = 0.1;
        
        @Comment("Only show particle effects to the target player (particles around peeker, but only target can see them)")
        public boolean onlyVisibleToTarget = false;
        
        @Comment("Maximum distance other players can be from peeker to see particles (0 = unlimited)")
        public double maxViewDistance = 50.0;
        
        @Comment("Particle color for dust particles (hex format like #FF0000 for red)")
        public String dustParticleColor = "#00FFFF";
        
        public ParticleSettings() {}
    }

}
