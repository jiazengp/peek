# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Build System & Multi-Version Support

This project uses a sophisticated multi-version build system supporting Minecraft 26.1+:

### Core Build Commands
```bash
# Build for the default version (26.1)
./gradlew build

# Build for a specific Minecraft version
./gradlew build -PmcVer=26.2

# Build for all supported versions
./buildAll.sh  # Linux/Mac
buildAll.bat   # Windows

# List all available Minecraft versions
./gradlew listVersions

# Run the development server (this is a server-side mod — use runServer, NOT runClient)
./gradlew runServer
```

### Version Configuration System
- Version properties are stored in `version_properties/*.properties` files
- Each version has specific dependency versions and preprocessor definitions
- The build system generates `build.properties` with preprocessor flags like `MC_VER=26010` for conditional compilation

### Preprocessor Usage
The project uses Manifold preprocessor for version compatibility:
```java
#if MC_VER >= 26010
    // Code for 26.1+
    return player.level();
#else
    // Code for earlier versions
    return player.getServerWorld();
#endif
```

## Architecture Overview

### Manager Pattern with Dependency Injection
The mod uses a centralized `ManagerRegistry` for dependency injection instead of singletons:

- **PlayerStateManager**: Handles saving/restoring player states, crash recovery
- **PeekSessionManager**: Manages active peek sessions, teleportation, session lifecycle
- **PeekRequestManager**: Handles request creation, acceptance, denial, timeouts
- **InviteManager**: Manages peek invitations between players
- **PeekStatisticsManager**: Tracks and persists usage statistics

Access managers via: `ManagerRegistry.getInstance().getManager(PlayerStateManager.class)`

### Data Storage Architecture
- **PlayerDataAPI Integration**: Per-player data storage with automatic save/load
- **PeekDataStorage**: Contains `PLAYER_PEEK_DATA_STORAGE` for player-specific data
- **PlayerPeekData**: Codec-based data structure for complex player state serialization
- **JsonCodecDataStorage**: Handles robust serialization of Minecraft objects like positions, effects, inventories

### Version Compatibility System
Located in `src/main/java/com/peek/utils/compat/`:

- **PlayerCompat**: Safe access to player methods across versions (getServerWorld, getGameMode, teleport, getRegistryManager)
- **ParticleCompat**: Handles particle API differences (spawnParticles method signatures, DustParticleEffect constructors)
- **TextEventCompat**: Manages text event API changes (ClickEvent, HoverEvent constructors)

### Session Management
Sessions follow a complex lifecycle with proper state restoration:

1. **SessionCreationContext**: Validates preconditions, captures original state
2. **TeleportationManager**: Handles cross-dimensional teleportation safely
3. **SessionUpdateHandler**: Manages real-time session updates (following, spectator mode)
4. **SessionCleanupService**: Ensures proper restoration on session end/crash

### Error Handling & Recovery
- **ExceptionHandler**: Centralized exception handling with user-friendly error messages
- **StateConsistencyChecker**: Validates player state integrity before operations
- **Crash Recovery**: Automatic state restoration using PlayerDataAPI on server restart

## Key Development Patterns

### Null Safety
Always use `PlayerCompat.getRegistryManager()` instead of direct `player.getServer().getRegistryManager()` calls to prevent NPE.

### Command Architecture
Commands use Brigadier with modular subcommand structure:
- Main `/peek` command delegates to subcommand modules (`PeekRequestCommands`, `PeekSettingsCommands`, `PeekUtilityCommands`, `PeekManageCommands`)
- Management commands in `/peek manage` subcommand tree
- Automatic permission checking and argument validation built into command structure

### Async Task Management
Use `TickTaskManager.scheduleDelayedTask()` for delayed operations instead of nested `server.execute()` calls.

### Configuration System
Uses ConfigLib with YAML support:
- Config file: `config/peek/config.yml`
- Loaded via `ModConfigManager.loadConfig()` at startup
- Access config values through static methods in `ModConfigManager`
- Runtime reloading supported via `/peek manage reload`

## Important Implementation Notes

### Version Compatibility
When adding new features, consider MC version differences and use appropriate preprocessor directives. Common differences:
- Teleport method signatures (26.1 vs 26.2+)
- Player method return types (World vs ServerWorld)
- Particle effect constructors (Vector3f vs int for colors)

### State Management
Player state capture/restoration is critical for mod functionality. Always validate states before restoration and handle edge cases like cross-dimensional teleportation.

### Permission Integration
The mod integrates with LuckPerms and Fabric Permissions API. Permission checks should use `PermissionChecker.hasPermission()` for consistency.

### Data Persistence
Player data is automatically managed by PlayerDataAPI. For global data, use the custom `JsonCodecDataStorage` implementations for complex structures.

## CI/CD and Testing

### GitHub Actions
The project uses matrix builds to test all supported Minecraft versions:
- Automatically builds for each version in `version_properties/` on PR/push
- Uses Java version specified in version_properties (currently Java 25) with Microsoft distribution
- Stores build artifacts and reports for debugging failures

### Multi-Version Testing
- CI automatically discovers available versions from `version_properties/*.properties`
- Each version builds independently to catch version-specific issues
- Build artifacts are uploaded for each Minecraft version