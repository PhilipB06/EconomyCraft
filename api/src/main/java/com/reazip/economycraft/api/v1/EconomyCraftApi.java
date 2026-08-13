package com.reazip.economycraft.api.v1;

import net.minecraft.server.MinecraftServer;

/** Stable, server-side entry point for EconomyCraft API v1. */
public interface EconomyCraftApi {
    static EconomyCraftApi get(MinecraftServer server) {
        return EconomyCraftApiAccess.get(server);
    }

    BalanceApi balances();

    PriceApi prices();

    LeaderboardApi leaderboard();

    BalanceEvents balanceEvents();

    String formatMoney(long amount);
}
