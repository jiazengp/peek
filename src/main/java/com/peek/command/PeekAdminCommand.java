package com.peek.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.peek.PeekMod;
import com.peek.config.ModConfigManager;
import com.peek.data.peek.PeekSession;
import com.peek.data.peek.PlayerPeekStats;
import com.peek.manager.ManagerRegistry;
import com.peek.manager.PeekSessionManager;
import com.peek.manager.PeekStatisticsManager;
import com.peek.utils.*;
import com.peek.manager.constants.PeekConstants;
import com.peek.utils.permissions.Permissions;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.peek.utils.permissions.PermissionChecker.hasPermission;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Admin commands for peek management
 */
public class PeekAdminCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("peekadmin")
            .requires(source -> hasPermission(source, Permissions.ADMIN, 2))
            .then(createStatsCommand())
            .then(createTopCommand())
            .then(createListCommand())
            .then(createPlayerCommand())
            .then(createSessionsCommand())
            .then(createCleanupCommand())
            .then(createForceStopCommand())
            .then(createReloadCommand())
            .then(createAboutCommand())
            .executes(PeekAdminCommand::showUsage));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createStatsCommand() {
        return literal("stats")
                .requires(source -> hasPermission(source, Permissions.Admin.STATS, 2))
                .executes(PeekAdminCommand::showGlobalStats);
    }
    
    private static LiteralArgumentBuilder<ServerCommandSource> createTopCommand() {
        return literal("top")
                .requires(source -> hasPermission(source, Permissions.Admin.TOP, 2))
                .executes(ctx -> showTopPlayers(ctx, 0))
                .then(CommandManager.argument("page", IntegerArgumentType.integer(0))
                    .executes(ctx -> showTopPlayers(ctx, IntegerArgumentType.getInteger(ctx, "page"))));
    }
    
    private static LiteralArgumentBuilder<ServerCommandSource> createListCommand() {
        return literal("list")
                .requires(source -> hasPermission(source, Permissions.Admin.LIST, 2))
                .executes(ctx -> showPlayerList(ctx, 0, PeekConstants.SortType.PEEK_COUNT))
                .then(CommandManager.argument("page", IntegerArgumentType.integer(0))
                    .executes(ctx -> showPlayerList(ctx, IntegerArgumentType.getInteger(ctx, "page"), PeekConstants.SortType.PEEK_COUNT))
                    .then(CommandManager.argument("sort", StringArgumentType.string())
                        .executes(ctx -> showPlayerList(ctx, IntegerArgumentType.getInteger(ctx, "page"), 
                            PeekConstants.SortType.fromKey(StringArgumentType.getString(ctx, "sort"))))));
    }
    
    private static LiteralArgumentBuilder<ServerCommandSource> createPlayerCommand() {
        return literal("player")
                .requires(source -> hasPermission(source, Permissions.Admin.PLAYER, 2))
                .then(CommandManager.argument("target", EntityArgumentType.player())
                    .executes(PeekAdminCommand::showPlayerDetails));
    }
    
    private static LiteralArgumentBuilder<ServerCommandSource> createSessionsCommand() {
        return literal("sessions")
                .requires(source -> hasPermission(source, Permissions.Admin.SESSIONS, 2))
                .executes(PeekAdminCommand::showActiveSessions);
    }
    
    private static LiteralArgumentBuilder<ServerCommandSource> createCleanupCommand() {
        return literal("cleanup")
                .requires(source -> hasPermission(source, Permissions.Admin.CLEANUP, 3))
                .executes(PeekAdminCommand::performCleanup);
    }
    
    private static LiteralArgumentBuilder<ServerCommandSource> createForceStopCommand() {
        return literal("force-stop")
                .requires(source -> hasPermission(source, Permissions.Admin.FORCE_STOP, 3))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(PeekAdminCommand::forceStopSession));
    }
    
    private static LiteralArgumentBuilder<ServerCommandSource> createReloadCommand() {
        return literal("reload")
                .requires(source -> hasPermission(source, Permissions.Manage.RELOAD, 3))
                .executes(PeekAdminCommand::reloadConfiguration);
    }
    
    private static LiteralArgumentBuilder<ServerCommandSource> createAboutCommand() {
        return literal("about")
                .requires(src -> hasPermission(src, Permissions.Manage.ABOUT, 2))
                .executes(PeekAdminCommand::showAboutInfo);
    }
    
    private static int showAboutInfo(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executeCommand(context, () -> {
            ServerCommandSource source = context.getSource();
            
            // Header
            source.sendMessage(CommandMessageUtils.createHeader("peek.admin.about.header"));
            
            // Create URL-aware info line sender
            BiConsumer<String, String> sendInfoLine = CommandMessageUtils.createUrlInfoLineSender(source);
            
            // Mod information
            sendInfoLine.accept("peek.admin.about.mod_id", PeekMod.MOD_ID);
            sendInfoLine.accept("peek.admin.about.name", ModMetadataHolder.MOD_NAME);
            sendInfoLine.accept("peek.admin.about.author", ModMetadataHolder.AUTHORS.stream()
                    .map(Person::getName)
                    .collect(Collectors.joining(", ")));
            sendInfoLine.accept("peek.admin.about.version", ModMetadataHolder.VERSION);
            sendInfoLine.accept("peek.admin.about.source_code", ModMetadataHolder.SOURCE);
            sendInfoLine.accept("peek.admin.about.issues", ModMetadataHolder.ISSUES);
            sendInfoLine.accept("peek.admin.about.homepage", ModMetadataHolder.HOMEPAGE);
            sendInfoLine.accept("peek.admin.about.license", ModMetadataHolder.LICENSE);

            // Footer
            source.sendMessage(CommandMessageUtils.createSeparator("peek.admin.about.separator"));
            
            return 1;
        });
    }


    private static int showUsage(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executeCommand(context, () -> {
            String[] usageKeys = {"stats", "top", "list", "player", "sessions", "cleanup", "force_stop", "reload", "about"};
            Formatting[] colors = {Formatting.YELLOW, Formatting.YELLOW, Formatting.YELLOW, 
                                 Formatting.YELLOW, Formatting.YELLOW, Formatting.YELLOW, Formatting.RED, Formatting.GREEN, Formatting.AQUA};
            
            MutableText usage = Text.translatable("peek.admin.usage.header").formatted(Formatting.GOLD, Formatting.BOLD);
            
            for (int i = 0; i < usageKeys.length; i++) {
                usage.append(TextUtils.newline().append(Text.translatable("peek.admin.usage." + usageKeys[i])).formatted(colors[i]));
            }
            
            usage.append(Text.literal("\n\n").append(Text.translatable("peek.admin.usage.sort_options")).formatted(Formatting.GRAY));
            
            context.getSource().sendFeedback(() -> usage, false);
            return 1;
        });
    }
    
    private static int showGlobalStats(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executeCommand(context, () -> {
            Map<String, Object> stats = ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).getSummaryStats();
            Map<UUID, PeekSession> activeSessions = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).getActiveSessions();
            
            MutableText message = Text.translatable("peek.admin.stats_global").formatted(Formatting.GOLD, Formatting.BOLD);
            
            // Basic stats
            TextUtils.addStatLine(message, "Total Sessions", stats.getOrDefault("totalSessions", 0));
            TextUtils.addStatLine(message, "Total Duration", TextUtils.formatDuration((Long) stats.getOrDefault("totalDuration", 0L)));
            TextUtils.addStatLine(message, "Total Players", stats.getOrDefault("totalPlayers", 0));
            TextUtils.addStatLine(message, "Average Session", String.format("%.1f seconds", (Double) stats.getOrDefault("averageSessionDuration", 0.0)));
            TextUtils.addStatLine(message, "Active Sessions", activeSessions.size());
            
            // Top stats
            if (stats.containsKey("topPeeker")) {
                TextUtils.addColoredStat(message, "Top Peeker", stats.get("topPeeker") + " (" + stats.get("topPeekerCount") + ")", Formatting.AQUA);
            }
            if (stats.containsKey("mostPeeked")) {
                TextUtils.addColoredStat(message, "Most Peeked", stats.get("mostPeeked") + " (" + stats.get("mostPeekedCount") + ")", Formatting.LIGHT_PURPLE);
            }
            
            context.getSource().sendFeedback(() -> message, false);
            return 1;
        });
    }
    
    private static int showTopPlayers(CommandContext<ServerCommandSource> context, int page) {
        return CommandUtils.executeCommand(context, () -> {
            int pageSize = ModConfigManager.getDefaultPageSize();
            List<Map.Entry<UUID, PlayerPeekStats>> players = 
                ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).getTopPeekers(page, pageSize);
            
            if (!ValidationUtils.validateCollectionNotEmptyAdmin(players, 
                    Text.translatable("peek.admin.no_data"), context.getSource())) {
                return 0;
            }
            
            MutableText message = TextUtils.createPagedHeader("peek.admin.top_peekers", page);
            
            int rank = page * pageSize + 1;
            for (Map.Entry<UUID, PlayerPeekStats> entry : players) {
                PlayerPeekStats stats = entry.getValue();
                message.append(TextUtils.createRankEntry(rank++, stats.playerName(), 
                    stats.peekCount() + " peeks", 
                    String.format("%.1f", stats.getTotalPeekDurationMinutes()) + "m"));
            }
            
            TextUtils.addPaginationControls(message, page, ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).getTotalPages(pageSize), "peekadmin top");
            context.getSource().sendFeedback(() -> message, false);
            return 1;
        });
    }
    
    private static int showPlayerList(CommandContext<ServerCommandSource> context, int page, PeekConstants.SortType sortType) {
        return CommandUtils.executeCommand(context, () -> {
            int pageSize = ModConfigManager.getDefaultPageSize();
            List<Map.Entry<UUID, PlayerPeekStats>> players = 
                ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).getSortedPlayers(sortType, page, pageSize);
            
            if (!ValidationUtils.validateCollectionNotEmptyAdmin(players, 
                    Text.translatable("peek.admin.no_data"), context.getSource())) {
                return 0;
            }
            
            MutableText message = Text.translatable("peek.admin.list.header", 
                sortType.name().toLowerCase().replace('_', ' ')).formatted(Formatting.GOLD, Formatting.BOLD);
            message.append(Text.translatable("peek.admin.list.page_info", (page + 1)).formatted(Formatting.GRAY));
            
            int index = page * pageSize + 1;
            for (Map.Entry<UUID, PlayerPeekStats> entry : players) {
                PlayerPeekStats stats = entry.getValue();
                message.append(TextUtils.createListEntry(index++, stats.playerName(), 
                    "P:" + stats.peekCount(), 
                    "T:" + stats.peekedCount(), 
                    "D:" + String.format("%.1f", stats.getTotalPeekDurationMinutes()) + "m"));
            }
            
            message.append(Text.translatable("peek.admin.list.legend").formatted(Formatting.GRAY, Formatting.ITALIC));
            TextUtils.addPaginationControls(message, page, ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).getTotalPages(pageSize), "peekadmin list");
            
            context.getSource().sendFeedback(() -> message, false);
            return 1;
        });
    }
    
    private static int showPlayerDetails(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executeWithPlayer(context, "target", (target) -> {
            PlayerPeekStats stats = ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class)
                .getPlayerStats(target.getUuid(), target.getGameProfile().getName());
            
            MutableText message = Text.translatable("peek.admin.player.header", 
                target.getGameProfile().getName()).formatted(Formatting.GOLD, Formatting.BOLD);
            
            // Basic stats
            TextUtils.addStatLine(message, "Peek Count", stats.peekCount());
            TextUtils.addStatLine(message, "Peeked Count", stats.peekedCount());
            TextUtils.addStatLine(message, "Total Peek Duration", String.format("%.1f minutes", stats.getTotalPeekDurationMinutes()));
            TextUtils.addStatLine(message, "Total Peeked Duration", String.format("%.1f minutes", stats.totalPeekedDuration() / 60.0));
            
            // Average durations
            if (stats.peekCount() > 0) {
                TextUtils.addStatLine(message, "Average Peek Duration", String.format("%.1f seconds", stats.getAveragePeekDuration()));
            }
            if (stats.peekedCount() > 0) {
                TextUtils.addStatLine(message, "Average Peeked Duration", String.format("%.1f seconds", stats.getAveragePeekedDuration()));
            }
            
            // Current status
            PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
            boolean isPeeking = sessionManager.isPlayerPeeking(target.getUuid());
            boolean beingPeeked = sessionManager.isPlayerBeingPeeked(target.getUuid());
            
            String status = isPeeking && beingPeeked ? "Peeking someone, Being peeked" :
                          isPeeking ? "Peeking someone" :
                          beingPeeked ? "Being peeked" : "Idle";
            TextUtils.addStatLine(message, "Current Status", status);
            TextUtils.addStatLine(message, "Recent History Entries", stats.recentHistory().size());
            
            return message;
        }, (message) -> {
            context.getSource().sendFeedback(() -> message, false);
            return 1;
        });
    }
    
    private static int showActiveSessions(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executeCommand(context, () -> {
            Map<UUID, PeekSession> sessions = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).getActiveSessions();
            
            if (sessions.isEmpty()) {
                context.getSource().sendFeedback(() -> 
                    Text.translatable("peek.admin.no_sessions").formatted(Formatting.YELLOW), false);
                return 1;
            }
            
            MutableText message = Text.translatable("peek.admin.session_list").formatted(Formatting.GOLD, Formatting.BOLD);
            message.append(Text.translatable("peek.admin.sessions.active_count", sessions.size()).formatted(Formatting.GRAY));
            
            int index = 1;
            for (PeekSession session : sessions.values()) {
                message.append(TextUtils.createSessionEntry(index++, 
                    session.getPeekerName(), 
                    session.getTargetName(), 
                    TextUtils.formatDuration(session.getDurationSeconds()),
                    session.hasCrossedDimension(),
                    "/peekadmin force-stop " + session.getPeekerName()));
            }
            
            context.getSource().sendFeedback(() -> message, false);
            return 1;
        });
    }
    
    private static int performCleanup(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executeCommand(context, () -> {
            ServerCommandSource source = context.getSource();
            
            source.sendFeedback(() -> Text.translatable("peek.admin.cleanup_started").formatted(Formatting.YELLOW), false);
            ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).performCleanup();
            source.sendFeedback(() -> Text.translatable("peek.admin.cleanup_completed").formatted(Formatting.GREEN), false);
            
            return 1;
        });
    }
    
    private static int forceStopSession(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executeCommand(context, () -> {
            ServerCommandSource source = context.getSource();
            ServerPlayerEntity target = CommandUtils.getPlayerArgument(context, "player");
            
            if (target == null) {
                source.sendError(Text.translatable("peek.error.player_not_found"));
                return 0;
            }
            
            if (!ManagerRegistry.getInstance().getManager(PeekSessionManager.class).isPlayerPeeking(target.getUuid())) {
                source.sendError(Text.translatable("peek.admin.player_not_peeking"));
                return 0;
            }
            
            PeekConstants.Result<String> result = ManagerRegistry.getInstance().getManager(PeekSessionManager.class)
                .stopPeekSession(target.getUuid(), false, source.getServer());
            
            if (result.isSuccess()) {
                source.sendFeedback(() -> Text.translatable("peek.admin.session_stopped", 
                    target.getGameProfile().getName()).formatted(Formatting.GREEN), false);
                return 1;
            } else {
                source.sendError(Text.translatable("peek.admin.failed_to_stop", result.getError()));
                return 0;
            }
        });
    }
    
    private static int reloadConfiguration(CommandContext<ServerCommandSource> context) {
        return CommandUtils.executeCommand(context, () -> {
            try {
                // Reload the mod configuration
                ModConfigManager.reloadConfig();
                
                context.getSource().sendFeedback(() -> 
                    Text.translatable("peek.admin.config_reloaded")
                        .formatted(Formatting.GREEN), false);
                        
                return 1;
            } catch (Exception e) {
                context.getSource().sendError(
                    Text.translatable("peek.admin.config_reload_failed", e.getMessage()));
                return 0;
            }
        });
    }
}