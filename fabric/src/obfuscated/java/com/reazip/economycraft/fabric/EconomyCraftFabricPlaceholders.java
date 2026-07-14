package com.reazip.economycraft.fabric;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.PlaceholderResult;
import net.minecraft.resources.Identifier;

/** Kept separate from the entrypoint so it's only classloaded once placeholder-api is confirmed present. */
final class EconomyCraftFabricPlaceholders {
    private EconomyCraftFabricPlaceholders() {}

    static void register() {
        Placeholders.register(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "balance"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player!");
            EconomyManager eco = EconomyCraft.getManager(ctx.server());
            long balance = eco.getBalance(ctx.player().getUUID(), true);
            return PlaceholderResult.value(String.valueOf(balance));
        });

        Placeholders.register(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "balance_formatted"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player!");
            EconomyManager eco = EconomyCraft.getManager(ctx.server());
            long balance = eco.getBalance(ctx.player().getUUID(), true);
            return PlaceholderResult.value(EconomyCraft.formatMoney(balance));
        });

        Placeholders.register(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "daily_sell_remaining"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player!");
            EconomyManager eco = EconomyCraft.getManager(ctx.server());
            long remaining = eco.getDailySellRemaining(ctx.player().getUUID());
            return PlaceholderResult.value(remaining == Long.MAX_VALUE ? "∞" : String.valueOf(remaining));
        });
    }
}
