package com.reazip.economycraft.neoforge;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.util.PlaceholderValues;
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

final class EconomyCraftNeoForgeTab {
    private static final Logger LOGGER = LoggerFactory.getLogger("EconomyCraft/TAB");
    private static final int REFRESH_MS = 1000;
    private static final String NO_PLAYER = "[No player!]";
    private static final Pattern TOP_NAME = Pattern.compile("^%economycraft:top_name (\\d+)%$");
    private static final Pattern TOP_BALANCE = Pattern.compile("^%economycraft:top_balance (\\d+)%$");
    private static final Pattern TOP_BALANCE_FORMATTED = Pattern.compile("^%economycraft:top_balance_formatted (\\d+)%$");
    private static final Pattern TOP_BALANCE_SHORT = Pattern.compile("^%economycraft:top_balance_short (\\d+)%$");

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

        Function<Object, String> balance = tabPlayer ->
                PlaceholderValues.balance(EconomyCraft.getManager(server), uuidOf(getUniqueId, tabPlayer));
        registerPlayer.invoke(manager, "%economycraft:balance%", REFRESH_MS, balance);

        Function<Object, String> balanceFormatted = tabPlayer ->
                PlaceholderValues.balanceFormatted(EconomyCraft.getManager(server), uuidOf(getUniqueId, tabPlayer));
        registerPlayer.invoke(manager, "%economycraft:balance_formatted%", REFRESH_MS, balanceFormatted);

        Function<Object, String> balanceShort = tabPlayer ->
                PlaceholderValues.balanceShort(EconomyCraft.getManager(server), uuidOf(getUniqueId, tabPlayer));
        registerPlayer.invoke(manager, "%economycraft:balance_short%", REFRESH_MS, balanceShort);

        Function<Object, String> dailySellRemaining = tabPlayer ->
                PlaceholderValues.dailySellRemaining(EconomyCraft.getManager(server), uuidOf(getUniqueId, tabPlayer));
        registerPlayer.invoke(manager, "%economycraft:daily_sell_remaining%", REFRESH_MS, dailySellRemaining);

        Function<Matcher, Supplier<String>> topName = matcher -> {
            String rank = matcher.group(1);
            return () -> orNoPlayer(PlaceholderValues.topName(EconomyCraft.getManager(server), rank));
        };
        registerServerPattern.invoke(manager, TOP_NAME, REFRESH_MS, topName);

        Function<Matcher, Supplier<String>> topBalance = matcher -> {
            String rank = matcher.group(1);
            return () -> orNoPlayer(PlaceholderValues.topBalance(EconomyCraft.getManager(server), rank));
        };
        registerServerPattern.invoke(manager, TOP_BALANCE, REFRESH_MS, topBalance);

        Function<Matcher, Supplier<String>> topBalanceFormatted = matcher -> {
            String rank = matcher.group(1);
            return () -> orNoPlayer(PlaceholderValues.topBalanceFormatted(EconomyCraft.getManager(server), rank));
        };
        registerServerPattern.invoke(manager, TOP_BALANCE_FORMATTED, REFRESH_MS, topBalanceFormatted);

        Function<Matcher, Supplier<String>> topBalanceShort = matcher -> {
            String rank = matcher.group(1);
            return () -> orNoPlayer(PlaceholderValues.topBalanceShort(EconomyCraft.getManager(server), rank));
        };
        registerServerPattern.invoke(manager, TOP_BALANCE_SHORT, REFRESH_MS, topBalanceShort);
    }

    private static String orNoPlayer(String value) {
        return value != null ? value : NO_PLAYER;
    }

    private static UUID uuidOf(Method getUniqueId, Object tabPlayer) {
        try {
            return (UUID) getUniqueId.invoke(tabPlayer);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
