package com.reazip.economycraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import com.reazip.economycraft.auction.AuctionExpiration;
import com.reazip.economycraft.orders.OrderFulfillment;
import com.reazip.economycraft.util.AsyncFileWriter;
import com.reazip.economycraft.api.v1.EconomyCraftApiBootstrap;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.EconomyPaths;
import com.reazip.economycraft.util.IdentityCompat;
import com.reazip.economycraft.util.ProfileCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Pattern;

public final class EconomyCraft {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "economycraft";
    private static final Object MANAGER_LOCK = new Object();
    private static volatile EconomyManager manager;
    private static volatile MinecraftServer lastServer;
    private static final int EXPIRATION_CHECK_INTERVAL_TICKS = 20 * 60;

    public static void init() {
        if (EconomyCraftApiBootstrap.INITIALIZED == null) {
            throw new IllegalStateException("EconomyCraft API bootstrap failed");
        }
    }

    public static void onServerStarting(MinecraftServer server) {
        EconomyConfig.load(server);
        WebhookConfig.load(server);
    }

    public static void onServerStarted(MinecraftServer server) {
        getManager(server);
    }

    public static void onServerStopping(MinecraftServer server) {
        if (manager != null && lastServer == server) {
            manager.deactivate();
            manager.save();
        }
        AsyncFileWriter.flush();
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext,
                                        Commands.CommandSelection selection) {
        EconomyCommands.register(dispatcher, buildContext, selection);
    }

    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % EXPIRATION_CHECK_INTERVAL_TICKS != 0) return;

        EconomyManager eco = getManager(server);
        try {
            OrderFulfillment.expireOverdue(eco);
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to process order expirations", e);
        }
        try {
            AuctionExpiration.expireOverdue(eco);
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to process auction expirations", e);
        }
        try {
            eco.maybeRefreshDynamicPrices();
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to refresh dynamic shop prices", e);
        }
    }

    public static void onPlayerJoin(ServerPlayer player) {
        try {
            MinecraftServer server = player.level().getServer();
            ProfileCompat.cacheName(server, player.getUUID(), IdentityCompat.of(player).name());

            EconomyManager eco = getManager(server);
            if (eco.getBalance(player.getUUID(), false) == null) {
                eco.getBalance(player.getUUID(), true);
            } else {
                eco.refreshLeaderboard();
            }

            eco.markActive(player.getUUID());
            eco.getNotifications().sendPending(player);

            if (eco.getDeliveries().hasDeliveries(player.getUUID())) {
                sendPrompt(player, "You have unclaimed items: ", "[Claim]", "/eco deliveries");
            }

            if (EconomyPaths.hasSharedFolder(server)) {
                sendPrompt(player, "Found an older EconomyCraft setup. ", "[Import]", "/eco import");
            }
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to set up {} on join", player.getName().getString(), e);
        }
    }

    private static void sendPrompt(ServerPlayer player, String text, String label, String command) {
        ClickEvent ev = ChatCompat.runCommandEvent(command);

        if (ev != null) {
            Component msg = Component.literal(text)
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(label)
                            .withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev)));
            player.sendSystemMessage(msg);
        } else {
            ChatCompat.sendRunCommandTellraw(player, text, label, command);
        }
    }

    public static EconomyManager getManager(MinecraftServer server) {
        synchronized (MANAGER_LOCK) {
            if (manager == null || lastServer != server) {
                if (manager != null) manager.deactivate();
                manager = new EconomyManager(server);
                lastServer = server;
            }
            return manager;
        }
    }

    public static boolean canImportSharedFolder() {
        MinecraftServer server = lastServer;
        return server != null && EconomyPaths.hasSharedFolder(server);
    }

    public static void reloadFromDisk(MinecraftServer server) {
        synchronized (MANAGER_LOCK) {
            if (manager != null && lastServer == server) {
                manager.detach();
            }
            manager = null;
            lastServer = null;

            EconomyConfig.load(server);
            WebhookConfig.load(server);
            getManager(server);
        }
    }

    public static String formatMoney(long amount) {
        return withCurrency(amount < 0, groupedNumber(Math.abs(amount)));
    }

    public static String formatMoneyChat(long amount) {
        String name = EconomyConfig.get().display.currencyName;
        if (name.isEmpty()) return formatMoney(amount);
        return (amount < 0 ? "-" : "") + groupedNumber(Math.abs(amount)) + " " + name;
    }

    private static String groupedNumber(long value) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        symbols.setGroupingSeparator(EconomyConfig.get().display.balanceSeparator.charAt(0));
        return new DecimalFormat("#,##0", symbols).format(value);
    }

    private static String withCurrency(boolean negative, String number) {
        EconomyConfig.Display display = EconomyConfig.get().display;
        String symbol = display.currencySymbol;
        String sign = negative ? "-" : "";
        if (symbol.isEmpty()) return sign + number;
        if (display.currencySymbolPosition == EconomyConfig.CurrencyPosition.SUFFIX) {
            return sign + number + (Character.isLetter(symbol.codePointAt(0)) ? " " : "") + symbol;
        }
        return sign + symbol + (Character.isLetter(symbol.codePointBefore(symbol.length())) ? " " : "") + number;
    }

    public static String describeItem(int count, String name) {
        return count == 1 ? name : count + "x " + name;
    }

    public static String signedMoney(long amount) {
        return (amount < 0 ? "-" : "+") + formatMoney(Math.abs(amount));
    }

    public static String formatMultiplier(double multiplier) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        return new DecimalFormat("0.##", symbols).format(multiplier) + "x";
    }

    private static final String[] SHORT_MONEY_SUFFIXES = {"", "k", "M", "B", "T", "Qa", "Qi"};
    private static final Pattern SHORT_MONEY_NUMBER = Pattern.compile("\\d+\\.?\\d*|\\.\\d+");

    public static String formatMoneyShort(long amount) {
        long value = Math.abs(amount);
        int magnitude = 0;
        double scaled = value;
        while (scaled >= 1000 && magnitude < SHORT_MONEY_SUFFIXES.length - 1) {
            scaled /= 1000;
            magnitude++;
        }
        DecimalFormat format = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
        String number = format.format(scaled);
        if (magnitude < SHORT_MONEY_SUFFIXES.length - 1 && Double.parseDouble(number) >= 1000) {
            magnitude++;
            number = format.format(scaled / 1000);
        }
        return withCurrency(amount < 0, number + SHORT_MONEY_SUFFIXES[magnitude]);
    }

    public static boolean isShortMoneySuffix(String text) {
        for (String suffix : SHORT_MONEY_SUFFIXES) {
            if (!suffix.isEmpty() && suffix.equalsIgnoreCase(text)) return true;
        }
        return false;
    }

    public static @Nullable Long parseMoneyShort(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;

        Long parsed = parsePlainMoneyShort(s);
        if (parsed != null) return parsed;
        String stripped = stripCurrency(s);
        return stripped.equals(s) ? null : parsePlainMoneyShort(stripped);
    }

    private static String stripCurrency(String input) {
        EconomyConfig.Display display = EconomyConfig.get().display;
        String s = stripAffix(input, display.currencyName);
        return stripAffix(s, display.currencySymbol);
    }

    private static String stripAffix(String input, String affix) {
        if (affix.isEmpty() || input.length() <= affix.length()) return input;
        if (input.regionMatches(true, 0, affix, 0, affix.length())) {
            return input.substring(affix.length()).trim();
        }
        int start = input.length() - affix.length();
        if (input.regionMatches(true, start, affix, 0, affix.length())) {
            return input.substring(0, start).trim();
        }
        return input;
    }

    private static @Nullable Long parsePlainMoneyShort(String s) {
        if (s.isEmpty()) return null;

        for (int magnitude = SHORT_MONEY_SUFFIXES.length - 1; magnitude >= 1; magnitude--) {
            String suffix = SHORT_MONEY_SUFFIXES[magnitude];
            if (s.length() > suffix.length() && s.regionMatches(true, s.length() - suffix.length(), suffix, 0, suffix.length())) {
                String number = s.substring(0, s.length() - suffix.length()).trim();
                if (!SHORT_MONEY_NUMBER.matcher(number).matches()) return null;
                double scaled = Double.parseDouble(number) * Math.pow(1000, magnitude);
                if (scaled > Long.MAX_VALUE) return null;
                return Math.round(scaled);
            }
        }

        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
