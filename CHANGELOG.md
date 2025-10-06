# ChangeLog

## [v1.0.3] - 2025-01-XX

### Added

- **Minecraft 1.21.9 Support**: Full compatibility with Minecraft 1.21.9

---

## [v1.0.2]

### Added

- New `/peek manage` command system to replace `/peekadmin`
- Comprehensive management commands for statistics, player info, and session control
- Enhanced permission system with `peek.command.manage.*` structure
- Improved internationalization with translation keys for all management commands
- Enhanced `TextUtils` with better text handling for both String and Text objects

### Changed

- **BREAKING**: Migrated all admin commands from `/peekadmin` to `/peek manage`
- **BREAKING**: Updated permission nodes from `peek.admin.*` to `peek.command.manage.*`
- Refactored code organization with extracted constants in `GameConstants`
- Improved command structure with modular subcommand architecture
- Enhanced translation system to properly handle Minecraft Text objects

### Deprecated

- `/peekadmin` command (replaced by `/peek manage`)
- `peek.admin.*` permission nodes (use `peek.command.manage.*` instead)

### Removed

- `PeekAdminCommand.java` (functionality moved to `PeekManageCommands.java`)

### Fixed

- JSON syntax errors in language files
- Hardcoded text strings in management commands now use proper translation keys
- Text handling to use proper Minecraft Text objects instead of converting to strings
