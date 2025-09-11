package com.peek.utils.permissions;

// Removed ShareConstants import as it's no longer needed

public final class Permissions {
    // Root nodes
    public static final String ADMIN = "admin";
    public static final String COMMAND = "command";
    public static final String MANAGE = "manage";
    public static final String CHAT = "chat";
    
    // Command permissions for peek operations
    public enum CommandType {
        PEEK("peek"),
        ACCEPT("accept"),
        DENY("deny"),
        STOP("stop"),
        INVITE("invite"),
        SETTINGS("settings"),
        BLACKLIST("blacklist"),
        STATS("stats");

        private final String permission;
        
        CommandType(String command) {
            this.permission = node(COMMAND, command);
        }
        
        public String getPermission() {
            return permission;
        }

        public String getCooldown() {
            return node(permission, "cooldown");
        }

        public String receivers() {
            return node(permission, "receivers");
        }
        
        public String duration() {
            return node(permission, "duration");
        }
        
        public String description() {
            return node(permission, "description");
        }
        
        @Override
        public String toString() {
            return permission;
        }
    }
    
    // Command class for peek permissions
    public static final class Command {
        public static final String PEEK = CommandType.PEEK.getPermission();
        public static final String ACCEPT = CommandType.ACCEPT.getPermission();
        public static final String DENY = CommandType.DENY.getPermission();
        public static final String STOP = CommandType.STOP.getPermission();
        public static final String INVITE = CommandType.INVITE.getPermission();
        public static final String SETTINGS = CommandType.SETTINGS.getPermission();
        public static final String SETTINGS_PRIVATE = node(COMMAND, "settings", "private"); // Permission to modify private mode
        public static final String SETTINGS_AUTO_ACCEPT = node(COMMAND, "settings", "auto_accept"); // Permission to modify auto-accept
        public static final String BLACKLIST = CommandType.BLACKLIST.getPermission();
        public static final String BLACKLIST_ADD = node(COMMAND, "blacklist", "add"); // Permission to add players to blacklist
        public static final String BLACKLIST_REMOVE = node(COMMAND, "blacklist", "remove"); // Permission to remove players from blacklist  
        public static final String BLACKLIST_LIST = node(COMMAND, "blacklist", "list"); // Permission to view blacklist
        public static final String WHITELIST = node(COMMAND, "whitelist"); // Permission to use whitelist commands
        public static final String WHITELIST_ADD = node(COMMAND, "whitelist", "add"); // Permission to add players to whitelist
        public static final String WHITELIST_REMOVE = node(COMMAND, "whitelist", "remove"); // Permission to remove players from whitelist  
        public static final String WHITELIST_LIST = node(COMMAND, "whitelist", "list"); // Permission to view whitelist
        public static final String STATS = CommandType.STATS.getPermission();
        public static final String STATS_OTHERS = node(COMMAND, "stats", "others"); // Permission to view others' stats

        private Command() {}
    }
    
    // Admin permissions for administrative operations
    public static final class Admin {
        public static final String STATS = node(ADMIN, "stats");
        public static final String TOP = node(ADMIN, "top");
        public static final String LIST = node(ADMIN, "list");
        public static final String PLAYER = node(ADMIN, "player");
        public static final String SESSIONS = node(ADMIN, "sessions");
        public static final String CLEANUP = node(ADMIN, "cleanup");
        public static final String FORCE_STOP = node(ADMIN, "force_stop");
        
        private Admin() {}
    }
    
    public static final class Manage {
        public static final String ABOUT = node(MANAGE, "about");
        public static final String RELOAD = node(MANAGE, "reload");
        public static final String LIST = node(MANAGE, "list");
        public static final String CANCEL = node(MANAGE, "cancel");
        
        private Manage() {}
    }
    
    public static final class Chat {
        public static final String PLACEHOLDER = node(CHAT, "placeholder");
        
        private Chat() {}
    }
    
    // Bypass permissions for various restrictions
    public static final class Bypass {
        public static final String COOLDOWN = node("bypass", "cooldown");
        public static final String DISTANCE = node("bypass", "distance");
        public static final String TIME_LIMIT = node("bypass", "time_limit");
        public static final String MAX_SESSIONS = node("bypass", "max_sessions");
        public static final String PRIVATE_MODE = node("bypass", "private_mode");
        public static final String BLACKLIST = node("bypass", "blacklist");
        public static final String WHITELIST = node("bypass", "whitelist");
        public static final String DIMENSION = node("bypass", "dimension");
        public static final String MOVEMENT = node("bypass", "movement");
        public static final String NO_MOBS = node("bypass", "no_mobs"); // Permission to bypass hostile mob checks (default level: 2)
        
        private Bypass() {}
    }

    /**
     * Build permission node path
     * @param parts permission node parts
     * @return full permission path joined with dots
     */
    public static String node(String... parts) {
        return String.join(".", parts);
    }
    
    private Permissions() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}