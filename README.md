# Peek Mod

A server-side Minecraft mod that lets you see what your friends and other players are doing through simple command requests, without needing to teleport to them.

## ✨ Features

- **Player Observation**: Teleport to and observe from any player's viewpoint
- **State Protection**: Automatically saves and restores your position, items, and status effects
- **Flexible Switching**: Switch between different observation targets seamlessly  
- **Privacy Controls**: Private mode, blacklist/whitelist system, and auto-accept settings
- **Crash Recovery**: Restores your state even after unexpected server restarts
- **Fully Configurable**: All features, limits, and behaviors can be customized via configuration
- **LuckPerms Integration**: Complete permission node support for fine-grained access control
- **PlaceholderAPI Support**: Rich placeholder system for integration with other mods and plugins

## ⌨️ Commands

### Core Peek Commands

```
/peek <player>              # Start observing a player
/peek accept                # Accept an incoming peek request
/peek deny                  # Deny an incoming peek request  
/peek stop                  # Stop current peek session
/peek cancel [player]       # Cancel your outgoing request or specific player's
/peek who                   # Show who you're peeking or who's peeking you
/peek invite <player>       # Send a peek invitation
/peek stats [player]        # View peek statistics
/peek debug                 # Show debug information (ops only)
```

### Settings Commands

```
/peek settings private <on/off>          # Enable/disable private mode
/peek settings auto-accept <on/off>      # Enable/disable auto-accept requests
/peek settings blacklist <add/remove/list> [player]  # Manage blacklist
/peek settings whitelist <add/remove/list> [player]  # Manage whitelist
```

### Admin Commands

```
/peekadmin stats                    # View global statistics  
/peekadmin top [page]              # Show top players by peek count
/peekadmin list [page] [sort]      # List all players with stats
/peekadmin player <player>         # Show detailed player information
/peekadmin sessions                # View all active peek sessions
/peekadmin force-stop <player>     # Force stop a player's session
/peekadmin cleanup                 # Perform data cleanup
/peekadmin reload                  # Reload configuration
/peekadmin about                   # Show mod information
```

## 🔌 Developer API

### PlaceholdersAPI Integration

The mod provides several placeholders for use with other mods or display systems:

```
%peekmod:peek_count%      - Number of times the player has peeked others
%peekmod:peeked_count%    - Number of times the player has been peeked
%peekmod:total_duration%  - Total time spent peeking (formatted)
%peekmod:is_peeking%      - Whether player is currently peeking (true/false)
%peekmod:is_private%      - Whether player has private mode enabled (true/false)
```

## ⚙️ Configuration

Main configuration file: `config/peek/config.yml` (YAML format)

## 📝 License

[MIT License](./LICENSE)