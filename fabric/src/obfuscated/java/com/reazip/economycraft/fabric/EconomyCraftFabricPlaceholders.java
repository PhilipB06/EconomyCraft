package com.reazip.economycraft.fabric;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.PlaceholderValues;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.PlaceholderResult;
import net.minecraft.resources.Identifier;

final class EconomyCraftFabricPlaceholders {
    private EconomyCraftFabricPlaceholders() {}

    static void register() {
        Placeholders.register(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "balance"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player!");
            EconomyManager eco = EconomyCraft.getManager(ctx.server());
            return PlaceholderResult.value(PlaceholderValues.balance(eco, ctx.player().getUUID()));
        });

        Placeholders.register(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "balance_formatted"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player!");
            EconomyManager eco = EconomyCraft.getManager(ctx.server());
            return PlaceholderResult.value(PlaceholderValues.balanceFormatted(eco, ctx.player().getUUID()));
        });

        Placeholders.register(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "balance_short"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player!");
            EconomyManager eco = EconomyCraft.getManager(ctx.server());
            return PlaceholderResult.value(PlaceholderValues.balanceShort(eco, ctx.player().getUUID()));
        });

        Placeholders.register(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "daily_sell_remaining"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player!");
            EconomyManager eco = EconomyCraft.getManager(ctx.server());
            return PlaceholderResult.value(PlaceholderValues.dailySellRemaining(eco, ctx.player().getUUID()));
        });

        Placeholders.register(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "top_name"), (ctx, arg) -> {
            String result = PlaceholderValues.topName(EconomyCraft.getManager(ctx.server()), arg);
            return result != null ? PlaceholderResult.value(result) : PlaceholderResult.invalid("No player!");
        });

        Placeholders.register(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "top_balance"), (ctx, arg) -> {
            String result = PlaceholderValues.topBalance(EconomyCraft.getManager(ctx.server()), arg);
            return result != null ? PlaceholderResult.value(result) : PlaceholderResult.invalid("No player!");
        });

        Placeholders.register(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "top_balance_formatted"), (ctx, arg) -> {
            String result = PlaceholderValues.topBalanceFormatted(EconomyCraft.getManager(ctx.server()), arg);
            return result != null ? PlaceholderResult.value(result) : PlaceholderResult.invalid("No player!");
        });

        Placeholders.register(Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "top_balance_short"), (ctx, arg) -> {
            String result = PlaceholderValues.topBalanceShort(EconomyCraft.getManager(ctx.server()), arg);
            return result != null ? PlaceholderResult.value(result) : PlaceholderResult.invalid("No player!");
        });
    }
}
