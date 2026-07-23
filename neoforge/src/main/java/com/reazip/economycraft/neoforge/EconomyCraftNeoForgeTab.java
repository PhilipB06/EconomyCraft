package com.reazip.economycraft.neoforge;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Kept separate from the entrypoint so it's only classloaded once TAB is confirmed present. Uses reflection since TAB has no resolvable API dependency and no auto-bridge to placeholder-api-neoforge on NeoForge like it has on Fabric. */
final class EconomyCraftNeoForgeTab {
    private static final Logger LOGGER = LoggerFactory.getLogger("EconomyCraft/TAB");
    private static final int REFRESH_MS = 1000;
    // Matches PlaceholderResult.invalid("No player!")'s rendered text ("[" + reason + "]"), so a
    // missing leaderboard rank reads the same here as it does through placeholder-api-neoforge.
    private static final String NO_PLAYER = "[No player!]";
    private static final Pattern TOP_NAME = Pattern.compile("^%economycraft:top_name (\\d+)%$");
    private static final Pattern TOP_BALANCE = Pattern.compile("^%economycraft:top_balance (\\d+)%$");
    private static final Pattern TOP_BALANCE_FORMATTED = Pattern.compile("^%economycraft:top_balance_formatted (\\d+)%$");

    private EconomyCraftNeoForgeTab() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> {
            try {
                registerPlaceholders(event.getServer());
            } catch (ReflectiveOperationException e) {
                LOGGER.error("Failed to register economycraft placeholders with TAB", e);
            }
        });
    }

    private static void registerPlaceholders(MinecraftServer server) throws ReflectiveOperationException {
        Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
        Object tabApi = tabApiClass.getMethod("getInstance").invoke(null);
        Object manager = tabApiClass.getMethod("getPlaceholderManager").invoke(tabApi);
        Class<?> managerClass = Class.forName("me.neznamy.tab.api.placeholder.PlaceholderManager");
        Method getUniqueId = Class.forName("me.neznamy.tab.api.TabPlayer").getMethod("getUniqueId");

        Method registerPlayer = managerClass.getMethod("registerPlayerPlaceholder", String.class, int.class, Function.class);
        Method registerServerPattern = managerClass.getMethod("registerServerPlaceholder", Pattern.class, int.class, Function.class);

        Function<Object, String> balance = tabPlayer -> String.valueOf(
                EconomyCraft.getManager(server).getBalance(uuidOf(getUniqueId, tabPlayer), true));
        registerPlayer.invoke(manager, "%economycraft:balance%", REFRESH_MS, balance);

        Function<Object, String> balanceFormatted = tabPlayer -> EconomyCraft.formatMoney(
                EconomyCraft.getManager(server).getBalance(uuidOf(getUniqueId, tabPlayer), true));
        registerPlayer.invoke(manager, "%economycraft:balance_formatted%", REFRESH_MS, balanceFormatted);

        Function<Object, String> dailySellRemaining = tabPlayer -> {
            long remaining = EconomyCraft.getManager(server).getDailySellRemaining(uuidOf(getUniqueId, tabPlayer));
            return remaining == Long.MAX_VALUE ? "∞" : String.valueOf(remaining);
        };
        registerPlayer.invoke(manager, "%economycraft:daily_sell_remaining%", REFRESH_MS, dailySellRemaining);

        Function<Matcher, Supplier<String>> topName = matcher -> {
            int rank = Integer.parseInt(matcher.group(1));
            return () -> {
                EconomyManager.LeaderboardEntry entry = EconomyCraft.getManager(server).getLeaderboardEntry(rank);
                return entry != null ? entry.name() : NO_PLAYER;
            };
        };
        registerServerPattern.invoke(manager, TOP_NAME, REFRESH_MS, topName);

        Function<Matcher, Supplier<String>> topBalance = matcher -> {
            int rank = Integer.parseInt(matcher.group(1));
            return () -> {
                EconomyManager.LeaderboardEntry entry = EconomyCraft.getManager(server).getLeaderboardEntry(rank);
                return entry != null ? String.valueOf(entry.balance()) : NO_PLAYER;
            };
        };
        registerServerPattern.invoke(manager, TOP_BALANCE, REFRESH_MS, topBalance);

        Function<Matcher, Supplier<String>> topBalanceFormatted = matcher -> {
            int rank = Integer.parseInt(matcher.group(1));
            return () -> {
                EconomyManager.LeaderboardEntry entry = EconomyCraft.getManager(server).getLeaderboardEntry(rank);
                return entry != null ? EconomyCraft.formatMoney(entry.balance()) : NO_PLAYER;
            };
        };
        registerServerPattern.invoke(manager, TOP_BALANCE_FORMATTED, REFRESH_MS, topBalanceFormatted);
    }

    private static UUID uuidOf(Method getUniqueId, Object tabPlayer) {
        try {
            return (UUID) getUniqueId.invoke(tabPlayer);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
