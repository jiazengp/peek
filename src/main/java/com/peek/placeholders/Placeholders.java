package com.peek.placeholders;

import com.peek.data.PeekDataStorage;
import com.peek.data.peek.PlayerPeekData;
import com.peek.data.peek.PlayerPeekStats;
import com.peek.manager.ManagerRegistry;
import com.peek.manager.PeekSessionManager;
import com.peek.manager.PeekStatisticsManager;
import com.peek.utils.TextUtils;
import com.peek.utils.compat.ProfileCompat;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.ServerPlaceholderContext;
import eu.pb4.placeholders.api.node.LiteralNode;
import eu.pb4.placeholders.api.node.TextNode;
import eu.pb4.playerdata.api.PlayerDataApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import static com.peek.PeekMod.MOD_ID;

public class Placeholders {
    public static void registerPlaceholders() {
        // Register peek count placeholder
        eu.pb4.placeholders.api.Placeholders.registerCommon(Identifier.fromNamespaceAndPath(MOD_ID, "peek_count"), (ctx, args) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                PlayerPeekStats stats = ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).getPlayerStats(player.getUUID(), ProfileCompat.getName(player.getGameProfile()));
                return PlaceholderResult.value(Component.literal(String.valueOf(stats.peekCount())));
            }
            return PlaceholderResult.invalid("No player context");
        });

        // Register peeked count placeholder
        eu.pb4.placeholders.api.Placeholders.registerCommon(Identifier.fromNamespaceAndPath(MOD_ID, "peeked_count"), (ctx, args) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                PlayerPeekStats stats = ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).getPlayerStats(player.getUUID(), ProfileCompat.getName(player.getGameProfile()));
                return PlaceholderResult.value(Component.literal(String.valueOf(stats.peekedCount())));
            }
            return PlaceholderResult.invalid("No player context");
        });

        // Register total duration placeholder
        eu.pb4.placeholders.api.Placeholders.registerCommon(Identifier.fromNamespaceAndPath(MOD_ID, "total_duration"), (ctx, args) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                PlayerPeekStats stats = ManagerRegistry.getInstance().getManager(PeekStatisticsManager.class).getPlayerStats(player.getUUID(), ProfileCompat.getName(player.getGameProfile()));
                long totalSeconds = stats.totalPeekDuration();
                return PlaceholderResult.value(Component.literal(TextUtils.formatDuration(totalSeconds)));
            }
            return PlaceholderResult.invalid("No player context");
        });

        // Register is peeking placeholder
        eu.pb4.placeholders.api.Placeholders.registerCommon(Identifier.fromNamespaceAndPath(MOD_ID, "is_peeking"), (ctx, args) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                boolean isPeeking = ManagerRegistry.getInstance().getManager(PeekSessionManager.class).isPlayerPeeking(player.getUUID());
                return PlaceholderResult.value(Component.literal(String.valueOf(isPeeking)));
            }
            return PlaceholderResult.invalid("No player context");
        });

        // Register is private placeholder
        eu.pb4.placeholders.api.Placeholders.registerCommon(Identifier.fromNamespaceAndPath(MOD_ID, "is_private"), (ctx, args) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                PlayerPeekData playerData = PlayerDataApi.getCustomDataFor(player, PeekDataStorage.PLAYER_PEEK_DATA_STORAGE);
                if (playerData != null) {
                    return PlaceholderResult.value(Component.literal(String.valueOf(playerData.privateMode())));
                } else {
                    return PlaceholderResult.value(Component.literal("false"));
                }
            }
            return PlaceholderResult.invalid("No player context");
        });
    }


    public static boolean containsPlaceholders(String text) {
        TextNode[] nodes = eu.pb4.placeholders.api.Placeholders.COMMON_PLACEHOLDER_PARSER.parseNodes(
                new LiteralNode(text)
        );

        for (TextNode node : nodes) {
            if (node.isDynamic()) {
                return true;
            }
        }

        return false;
    }

    public static boolean isOnlyPlaceholders(String text, ServerPlaceholderContext context) {
        TextNode[] nodes = eu.pb4.placeholders.api.Placeholders.COMMON_PLACEHOLDER_PARSER.parseNodes(
                new LiteralNode(text)
        );

        for (TextNode node : nodes) {
            if (node instanceof LiteralNode(String value)) {
                if (!value.trim().isEmpty()) {
                    return false;
                }
            }
            else if (!node.isDynamic()) {
                return false;
            }
        }

        return true;
    }
}




