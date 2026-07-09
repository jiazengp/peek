# Changelog

## v2.0.0 (2026-07-08)

### Breaking Changes
- Drop support for Minecraft 1.21.x; now requires Minecraft 26.1+
- Migrate from Yarn mappings to Mojang mappings
- Require Java 25 (was Java 21)
- Require Fabric Loader >= 0.18.0
- Fabric API is now a required dependency

### Added
- Support for Minecraft 26.1, 26.1.2, and 26.2
- `fabric-api` as explicit dependency in fabric.mod.json
- Per-version `fabric_loader_min_version` configuration
- CI now uses dynamic Java version from version properties
- Session end callback supports custom messages

### Changed
- Update all API calls for Mojang mappings (ServerPlayerEntity→ServerPlayer, Text→Component, Vec3d→Vec3, etc.)
- Update compat layer with MC_VER >= 1219 preprocessor branches
- Refactor UserCacheCompat with simpler getNameByUuid API
- Update all dependency versions for Minecraft 26.x

### Fixed
- Fix java_version default value inconsistency between CI and Gradle

---

## v1.0.3 (2025-12-10)

- Support Minecraft 1.21.11
- Support Minecraft 1.21.9

## v1.0.2 (2025-10-15)

- Support Minecraft 1.21.6

## v1.0.0 (2025-08-01)

- Initial release
- Peek functionality with spectator teleport
- Multi-version support for Minecraft 1.21.1 - 1.21.6
