package com.reazip.economycraft.neoforge;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.PlaceholderResult;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/** Kept separate from the entrypoint so it's only classloaded once placeholder-api-neoforge is confirmed present. */
final class EconomyCraftNeoForgePlaceholders {
    private EconomyCraftNeoForgePlaceholders() {}

    static void register() {
        Placeholders.registerServer(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "balance"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player!");
            EconomyManager eco = EconomyCraft.getManager(ctx.server());
            long balance = eco.getBalance(ctx.player().getUUID(), true);
            return PlaceholderResult.value(String.valueOf(balance));
        });

        Placeholders.registerServer(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "balance_formatted"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player!");
            EconomyManager eco = EconomyCraft.getManager(ctx.server());
            long balance = eco.getBalance(ctx.player().getUUID(), true);
            return PlaceholderResult.value(EconomyCraft.formatMoney(balance));
        });

        Placeholders.registerServer(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "daily_sell_remaining"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player!");
            EconomyManager eco = EconomyCraft.getManager(ctx.server());
            long remaining = eco.getDailySellRemaining(ctx.player().getUUID());
            return PlaceholderResult.value(remaining == Long.MAX_VALUE ? "∞" : String.valueOf(remaining));
        });

        Placeholders.registerServer(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "top_name"), (ctx, arg) -> {
            EconomyManager.LeaderboardEntry entry = topEntry(ctx.server(), arg);
            return entry != null ? PlaceholderResult.value(entry.name()) : PlaceholderResult.invalid("No player!");
        });

        Placeholders.registerServer(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "top_balance"), (ctx, arg) -> {
            EconomyManager.LeaderboardEntry entry = topEntry(ctx.server(), arg);
            return entry != null ? PlaceholderResult.value(String.valueOf(entry.balance())) : PlaceholderResult.invalid("No player!");
        });

        Placeholders.registerServer(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "top_balance_formatted"), (ctx, arg) -> {
            EconomyManager.LeaderboardEntry entry = topEntry(ctx.server(), arg);
            return entry != null ? PlaceholderResult.value(EconomyCraft.formatMoney(entry.balance())) : PlaceholderResult.invalid("No player!");
        });
    }

    /** Parses {@code arg} as a 1-based leaderboard rank (e.g. "1" in "%economycraft:top_name 1%"). */
    private static EconomyManager.LeaderboardEntry topEntry(MinecraftServer server, String arg) {
        if (arg == null) return null;
        try {
            return EconomyCraft.getManager(server).getLeaderboardEntry(Integer.parseInt(arg.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
