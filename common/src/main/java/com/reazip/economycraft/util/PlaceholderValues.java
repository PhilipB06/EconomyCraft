package com.reazip.economycraft.util;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class PlaceholderValues {
    private PlaceholderValues() {}

    public static String balance(EconomyManager economy, UUID playerId) {
        return String.valueOf(economy.getBalance(playerId, true));
    }

    public static String balanceFormatted(EconomyManager economy, UUID playerId) {
        return EconomyCraft.formatMoney(economy.getBalance(playerId, true));
    }

    public static String balanceShort(EconomyManager economy, UUID playerId) {
        return EconomyCraft.formatMoneyShort(economy.getBalance(playerId, true));
    }

    public static String dailySellRemaining(EconomyManager economy, UUID playerId) {
        long remaining = economy.getDailySellRemaining(playerId);
        return remaining == Long.MAX_VALUE ? "∞" : String.valueOf(remaining);
    }

    public static @Nullable String topName(EconomyManager economy, @Nullable String arg) {
        EconomyManager.LeaderboardEntry entry = topEntry(economy, arg);
        return entry != null ? entry.name() : null;
    }

    public static @Nullable String topBalance(EconomyManager economy, @Nullable String arg) {
        EconomyManager.LeaderboardEntry entry = topEntry(economy, arg);
        return entry != null ? String.valueOf(entry.value()) : null;
    }

    public static @Nullable String topBalanceFormatted(EconomyManager economy, @Nullable String arg) {
        EconomyManager.LeaderboardEntry entry = topEntry(economy, arg);
        return entry != null ? EconomyCraft.formatMoney(entry.value()) : null;
    }

    public static @Nullable String topBalanceShort(EconomyManager economy, @Nullable String arg) {
        EconomyManager.LeaderboardEntry entry = topEntry(economy, arg);
        return entry != null ? EconomyCraft.formatMoneyShort(entry.value()) : null;
    }

    private static @Nullable EconomyManager.LeaderboardEntry topEntry(EconomyManager economy, @Nullable String arg) {
        if (arg == null) return null;
        try {
            return economy.getLeaderboardEntry(Integer.parseInt(arg.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
