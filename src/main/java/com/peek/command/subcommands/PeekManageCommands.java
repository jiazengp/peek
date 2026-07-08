package com.peek.command.subcommands;

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
import com.peek.utils.compat.ProfileCompat;
import com.peek.utils.permissions.Permissions;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.peek.utils.permissions.PermissionChecker.hasPermission;
import static net.minecraft.commands.Commands.literal;

/**
 * Management commands for peek administration
 */
public class PeekManageCommands {
    
    public static LiteralArgumentBuilder<CommandSourceStack> createManageCommand() {
        return literal("manage")
            .requires(source -> hasPermission(source, Permissions.ROOT_MANAGE, 2))
            .then(createStatsCommand())
            .then(createTopCommand())
            .then(createListCommand())
            .then(createPlayerCommand())
            .then(createSessionsCommand())
            .then(createCleanupCommand())
            .then(createForceStopCommand())
            .then(createReloadCommand())
            .then(createAboutCommand())
            .executes(PeekManageCommands::showUsage);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createStatsCommand() {
        return literal("stats")
                .requires(source -> hasPermission(source, Permissions.Manage.STATS, 2))
                .executes(PeekManageCommands::showGlobalStats);
    }
    
    private static LiteralArgumentBuilder<CommandSourceStack> createTopCommand() {
        return literal("top")
                .requires(source -> hasPermission(source, Permissions.Manage.TOP, 2))
                .executes(ctx -> showTopPlayers(ctx, 0))
                .then(Commands.argument("page", IntegerArgumentType.integer(0))
                    .executes(ctx -> showTopPlayers(ctx, IntegerArgumentType.getInteger(ctx, "page"))));
    }
    
    private static LiteralArgumentBuilder<CommandSourceStack> createListCommand() {
        return literal("list")
                .requires(source -> hasPermission(source, Permissions.Manage.LIST, 2))
                .executes(ctx -> showPlayerList(ctx, 0, PeekConstants.SortType.PEEK_COUNT))
                .then(Commands.argument("page", IntegerArgumentType.integer(0))
                    .executes(ctx -> showPlayerList(ctx, IntegerArgumentType.getInteger(ctx, "page"), PeekConstants.SortType.PEEK_COUNT))
                    .then(Commands.argument("sort", StringArgumentType.string())
                        .executes(ctx -> showPlayerList(ctx, IntegerArgumentType.getInteger(ctx, "page"), 
                            PeekConstants.SortType.fromKey(StringArgumentType.getString(ctx, "sort"))))));
    }
    
    private static LiteralArgumentBuilder<CommandSourceStack> createPlayerCommand() {
        return literal("player")
                .requires(source -> hasPermission(source, Permissions.Manage.PLAYER, 2))
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(PeekManageCommands::showPlayerDetails));
    }
    
    private static LiteralArgumentBuilder<CommandSourceStack> createSessionsCommand() {
        return literal("sessions")
                .requires(source -> hasPermission(source, Permissions.Manage.SESSIONS, 2))
                .executes(PeekManageCommands::showActiveSessions);
    }
    
    private static LiteralArgumentBuilder<CommandSourceStack> createCleanupCommand() {
        return literal("cleanup")
                .requires(source -> hasPermission(source, Permissions.Manage.CLEANUP, 3))
                .executes(PeekManageCommands::performCleanup);
    }
    
    private static LiteralArgumentBuilder<CommandSourceStack> createForceStopCommand() {
        return literal("force-stop")
                .requires(source -> hasPermission(source, Permissions.Manage.FORCE_STOP, 3))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(PeekManageCommands::forceStopSession));
    }
    
    private static LiteralArgumentBuilder<CommandSourceStack> createReloadCommand() {
        return literal("reload")
                .requires(source -> hasPermission(source, Permissions.Manage.RELOAD, 3))
                .executes(PeekManageCommands::reloadConfiguration);
    }
    
    private static LiteralArgumentBuilder<CommandSourceStack> createAboutCommand() {
        return literal("about")
                .requires(src -> hasPermission(src, Permissions.Manage.ABOUT, 2))
                .executes(PeekManageCommands::showAboutInfo);
    }
    
    private static int showAboutInfo(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executeCommand(context, () -> {
            CommandSourceStack source = context.getSource();
            
            // Header
            source.sendSystemMessage(CommandMessageUtils.createHeader("peek.manage.about.header"));
            
            // Create URL-aware info line sender
            BiConsumer<String, String> sendInfoLine = CommandMessageUtils.createUrlInfoLineSender(source);
            
            // Mod information
            sendInfoLine.accept("peek.manage.about.mod_id", PeekMod.MOD_ID);
            sendInfoLine.accept("peek.manage.about.name", ModMetadataHolder.MOD_NAME);
            sendInfoLine.accept("peek.manage.about.author", ModMetadataHolder.AUTHORS.stream()
                    .map(Person::getName)
                    .collect(Collectors.joining(", ")));
            sendInfoLine.accept("peek.manage.about.version", ModMetadataHolder.VERSION);
            sendInfoLine.accept("peek.manage.about.source_code", ModMetadataHolder.SOURCE);
            sendInfoLine.accept("peek.manage.about.issues", ModMetadataHolder.ISSUES);
            sendInfoLine.accept("peek.manage.about.homepage", ModMetadataHolder.HOMEPAGE);
            sendInfoLine.accept("peek.manage.about.license", ModMetadataHolder.LICENSE);

            // Footer
            source.sendSystemMessage(CommandMessageUtils.createSeparator("peek.manage.about.separator"));
            
            return 1;
        });
    }

    private static int showUsage(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executeCommand(context, () -> {
            String[] usageKeys = {"stats", "top", "list", "player", "sessions", "cleanup", "force_stop", "reload", "about"};
            ChatFormatting[] colors = {ChatFormatting.YELLOW, ChatFormatting.YELLOW, ChatFormatting.YELLOW, 
                                 ChatFormatting.YELLOW, ChatFormatting.YELLOW, ChatFormatting.YELLOW, ChatFormatting.RED, ChatFormatting.GREEN, ChatFormatting.AQUA};
            
            MutableComponent usage = Component.translatable("peek.manage.usage.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            
            for (int i = 0; i < usageKeys.length; i++) {
                usage.append(TextUtils.newline().append(Component.translatable("peek.manage.usage." + usageKeys[i])).withStyle(colors[i]));
            }
            
            usage.append(Component.literal("\n\n").append(Component.translatable("peek.manage.usage.sort_options")).withStyle(ChatFormatting.GRAY));
            
            context.getSource().sendSuccess(() -> usage, false);
            return 1;
        });
    }
    
    private static int showGlobalStats(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executeCommand(context, () -> {
            Map<String, Object> stats = ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).getSummaryStats();
            Map<UUID, PeekSession> activeSessions = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).getActiveSessions();
            
            MutableComponent message = Component.translatable("peek.manage.stats_global").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            
            // Basic stats
            TextUtils.addStatLine(message, Component.translatable("peek.manage.stats.total_sessions"), stats.getOrDefault("totalSessions", 0));
            TextUtils.addStatLine(message, Component.translatable("peek.manage.stats.total_duration"), TextUtils.formatDuration((Long) stats.getOrDefault("totalDuration", 0L)));
            TextUtils.addStatLine(message, Component.translatable("peek.manage.stats.total_players"), stats.getOrDefault("totalPlayers", 0));
            TextUtils.addStatLine(message, Component.translatable("peek.manage.stats.average_session"), String.format("%.1f seconds", (Double) stats.getOrDefault("averageSessionDuration", 0.0)));
            TextUtils.addStatLine(message, Component.translatable("peek.manage.stats.active_sessions"), activeSessions.size());
            
            // Top stats
            if (stats.containsKey("topPeeker")) {
                TextUtils.addColoredStat(message, Component.translatable("peek.manage.stats.top_peeker"), stats.get("topPeeker") + " (" + stats.get("topPeekerCount") + ")", ChatFormatting.AQUA);
            }
            if (stats.containsKey("mostPeeked")) {
                TextUtils.addColoredStat(message, Component.translatable("peek.manage.stats.most_peeked"), stats.get("mostPeeked") + " (" + stats.get("mostPeekedCount") + ")", ChatFormatting.LIGHT_PURPLE);
            }
            
            context.getSource().sendSuccess(() -> message, false);
            return 1;
        });
    }
    
    private static int showTopPlayers(CommandContext<CommandSourceStack> context, int page) {
        return CommandUtils.executeCommand(context, () -> {
            int pageSize = ModConfigManager.getDefaultPageSize();
            List<Map.Entry<UUID, PlayerPeekStats>> players = 
                ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).getTopPeekers(page, pageSize);
            
            if (!ValidationUtils.validateCollectionNotEmptyAdmin(players, 
                    Component.translatable("peek.manage.no_data"), context.getSource())) {
                return 0;
            }
            
            MutableComponent message = TextUtils.createPagedHeader("peek.manage.top_peekers", page);
            
            int rank = page * pageSize + 1;
            for (Map.Entry<UUID, PlayerPeekStats> entry : players) {
                PlayerPeekStats stats = entry.getValue();
                message.append(TextUtils.createRankEntry(rank++, stats.playerName(), 
                    stats.peekCount() + " peeks", 
                    String.format("%.1f", stats.getTotalPeekDurationMinutes()) + "m"));
            }
            
            TextUtils.addPaginationControls(message, page, ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).getTotalPages(pageSize), "peek manage top");
            context.getSource().sendSuccess(() -> message, false);
            return 1;
        });
    }
    
    private static int showPlayerList(CommandContext<CommandSourceStack> context, int page, PeekConstants.SortType sortType) {
        return CommandUtils.executeCommand(context, () -> {
            int pageSize = ModConfigManager.getDefaultPageSize();
            List<Map.Entry<UUID, PlayerPeekStats>> players = 
                ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).getSortedPlayers(sortType, page, pageSize);
            
            if (!ValidationUtils.validateCollectionNotEmptyAdmin(players, 
                    Component.translatable("peek.manage.no_data"), context.getSource())) {
                return 0;
            }
            
            MutableComponent message = Component.translatable("peek.manage.list.header", 
                sortType.name().toLowerCase().replace('_', ' ')).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            message.append(Component.translatable("peek.manage.list.page_info", (page + 1)).withStyle(ChatFormatting.GRAY));
            
            int index = page * pageSize + 1;
            for (Map.Entry<UUID, PlayerPeekStats> entry : players) {
                PlayerPeekStats stats = entry.getValue();
                message.append(TextUtils.createListEntry(index++, stats.playerName(), 
                    "P:" + stats.peekCount(), 
                    "T:" + stats.peekedCount(), 
                    "D:" + String.format("%.1f", stats.getTotalPeekDurationMinutes()) + "m"));
            }
            
            message.append(Component.translatable("peek.manage.list.legend").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            TextUtils.addPaginationControls(message, page, ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).getTotalPages(pageSize), "peek manage list");
            
            context.getSource().sendSuccess(() -> message, false);
            return 1;
        });
    }
    
    private static int showPlayerDetails(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executeWithPlayer(context, "target", (target) -> {
            PlayerPeekStats stats = ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class)
                .getPlayerStats(target.getUUID(), ProfileCompat.getName(target.getGameProfile()));

            MutableComponent message = Component.translatable("peek.manage.player.header",
                ProfileCompat.getName(target.getGameProfile())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            
            // Basic stats
            TextUtils.addStatLine(message, Component.translatable("peek.manage.player.peek_count"), stats.peekCount());
            TextUtils.addStatLine(message, Component.translatable("peek.manage.player.peeked_count"), stats.peekedCount());
            TextUtils.addStatLine(message, Component.translatable("peek.manage.player.total_peek_duration"), String.format("%.1f minutes", stats.getTotalPeekDurationMinutes()));
            TextUtils.addStatLine(message, Component.translatable("peek.manage.player.total_peeked_duration"), String.format("%.1f minutes", stats.totalPeekedDuration() / 60.0));
            
            // Average durations
            if (stats.peekCount() > 0) {
                TextUtils.addStatLine(message, Component.translatable("peek.manage.player.average_peek_duration"), String.format("%.1f seconds", stats.getAveragePeekDuration()));
            }
            if (stats.peekedCount() > 0) {
                TextUtils.addStatLine(message, Component.translatable("peek.manage.player.average_peeked_duration"), String.format("%.1f seconds", stats.getAveragePeekedDuration()));
            }
            
            // Current status
            PeekSessionManager sessionManager = ManagerRegistry.getInstance().getManager(PeekSessionManager.class);
            boolean isPeeking = sessionManager.isPlayerPeeking(target.getUUID());
            boolean beingPeeked = sessionManager.isPlayerBeingPeeked(target.getUUID());
            
            Component statusText = isPeeking && beingPeeked ? Component.translatable("peek.manage.player.status.peeking_and_peeked") :
                          isPeeking ? Component.translatable("peek.manage.player.status.peeking") :
                          beingPeeked ? Component.translatable("peek.manage.player.status.being_peeked") : Component.translatable("peek.manage.player.status.idle");
            TextUtils.addStatLine(message, Component.translatable("peek.manage.player.current_status"), statusText);
            TextUtils.addStatLine(message, Component.translatable("peek.manage.player.recent_history_entries"), stats.recentHistory().size());
            
            return message;
        }, (message) -> {
            context.getSource().sendSuccess(() -> message, false);
            return 1;
        });
    }
    
    private static int showActiveSessions(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executeCommand(context, () -> {
            Map<UUID, PeekSession> sessions = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).getActiveSessions();
            
            if (sessions.isEmpty()) {
                context.getSource().sendSuccess(() -> 
                    Component.translatable("peek.manage.no_sessions").withStyle(ChatFormatting.YELLOW), false);
                return 1;
            }
            
            MutableComponent message = Component.translatable("peek.manage.session_list").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            message.append(Component.translatable("peek.manage.sessions.active_count", sessions.size()).withStyle(ChatFormatting.GRAY));
            
            int index = 1;
            for (PeekSession session : sessions.values()) {
                message.append(TextUtils.createSessionEntry(index++, 
                    session.getPeekerName(), 
                    session.getTargetName(), 
                    TextUtils.formatDuration(session.getDurationSeconds()),
                    session.hasCrossedDimension(),
                    "/peek manage force-stop " + session.getPeekerName()));
            }
            
            context.getSource().sendSuccess(() -> message, false);
            return 1;
        });
    }
    
    private static int performCleanup(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executeCommand(context, () -> {
            CommandSourceStack source = context.getSource();
            
            source.sendSuccess(() -> Component.translatable("peek.manage.cleanup_started").withStyle(ChatFormatting.YELLOW), false);
            ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).performCleanup();
            source.sendSuccess(() -> Component.translatable("peek.manage.cleanup_completed").withStyle(ChatFormatting.GREEN), false);
            
            return 1;
        });
    }
    
    private static int forceStopSession(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executeCommand(context, () -> {
            CommandSourceStack source = context.getSource();
            ServerPlayer target = CommandUtils.getPlayerArgument(context, "player");
            
            if (target == null) {
                source.sendFailure(Component.translatable("peek.error.player_not_found"));
                return 0;
            }
            
            if (!ManagerRegistry.getInstance().getManager(PeekSessionManager.class).isPlayerPeeking(target.getUUID())) {
                source.sendFailure(Component.translatable("peek.manage.player_not_peeking"));
                return 0;
            }
            
            PeekConstants.Result<String> result = ManagerRegistry.getInstance().getManager(PeekSessionManager.class)
                .stopPeekSession(target.getUUID(), false, source.getServer());
            
            if (result.isSuccess()) {
                source.sendSuccess(() -> Component.translatable("peek.manage.session_stopped",
                    ProfileCompat.getName(target.getGameProfile())).withStyle(ChatFormatting.GREEN), false);
                return 1;
            } else {
                source.sendFailure(Component.translatable("peek.manage.failed_to_stop", result.getError()));
                return 0;
            }
        });
    }
    
    private static int reloadConfiguration(CommandContext<CommandSourceStack> context) {
        return CommandUtils.executeCommand(context, () -> {
            try {
                // Reload the mod configuration
                ModConfigManager.reloadConfig();
                
                context.getSource().sendSuccess(() -> 
                    Component.translatable("peek.manage.config_reloaded")
                        .withStyle(ChatFormatting.GREEN), false);
                        
                return 1;
            } catch (Exception e) {
                context.getSource().sendFailure(
                    Component.translatable("peek.manage.config_reload_failed", e.getMessage()));
                return 0;
            }
        });
    }
}


