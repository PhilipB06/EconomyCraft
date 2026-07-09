package com.reazip.economycraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.reazip.economycraft.PriceRegistry.ResolvedPrice;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.IdentifierCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class SellCommand {
    private static final Map<UUID, PendingSale> PENDING = new HashMap<>();
    private static final Map<UUID, PendingEverything> PENDING_EVERYTHING = new HashMap<>();
    private static final Map<UUID, PendingHand> PENDING_HAND = new HashMap<>();
    private static final long CONFIRM_EXPIRY_MS = 20_000L;

    private SellCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return literal("sell")
                .then(literal("all")
                        .executes(SellCommand::previewSellAll)
                        .then(literal("confirm").executes(SellCommand::confirmSellAll)))
                .then(literal("everything")
                        .executes(SellCommand::previewSellEverything)
                        .then(literal("confirm").executes(SellCommand::confirmSellEverything)))
                .then(literal("confirm").executes(SellCommand::confirmSellHand))
                .then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> sellMainHand(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))
                .executes(ctx -> sellMainHand(ctx, -1));
    }

    /** Holding item + price validation shared by sellMainHand and previewSellAll. */
    private record SellContext(CommandSourceStack source, ServerPlayer player, ItemStack hand,
                                EconomyManager manager, PriceRegistry prices,
                                ResolvedPrice resolved, long unitSell) {}

    @Nullable
    private static SellContext validateSellable(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) return null;

        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) {
            source.sendFailure(Component.literal("You are not holding any item.").withStyle(ChatFormatting.RED));
            return null;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        PriceRegistry prices = manager.getPrices();

        ResolvedPrice resolved = prices.resolve(hand);
        Long unitSell = prices.getUnitSell(hand);
        if (resolved == null || unitSell == null) {
            source.sendFailure(Component.literal("This item cannot be sold.").withStyle(ChatFormatting.RED));
            return null;
        }

        if (prices.isSellBlockedByDamage(hand)) {
            source.sendFailure(Component.literal("Damaged items cannot be sold.").withStyle(ChatFormatting.RED));
            return null;
        }

        if (prices.isSellBlockedByContents(hand)) {
            source.sendFailure(Component.literal("Items with contents cannot be sold.").withStyle(ChatFormatting.RED));
            return null;
        }

        return new SellContext(source, player, hand, manager, prices, resolved, unitSell);
    }

    private static int sellMainHand(CommandContext<CommandSourceStack> ctx, int amount) {
        SellContext sc = validateSellable(ctx);
        if (sc == null) return 0;
        CommandSourceStack source = sc.source();
        ServerPlayer player = sc.player();
        ItemStack hand = sc.hand();

        int available = hand.getCount();
        int toSell = amount < 0 ? available : amount;
        if (toSell < 1 || toSell > available) {
            source.sendFailure(Component.literal("Invalid amount.").withStyle(ChatFormatting.RED));
            return 0;
        }

        Long total = safeMultiply(sc.unitSell(), toSell);
        if (total == null) {
            source.sendFailure(Component.literal("Sale amount is too large.").withStyle(ChatFormatting.RED));
            return 0;
        }

        // Enchanted gear resells at its base price only; require a confirmation so players don't
        // dump valuable items by accident.
        if (hand.isEnchanted()) {
            PENDING_HAND.put(player.getUUID(), new PendingHand(toSell, System.currentTimeMillis() + CONFIRM_EXPIRY_MS));
            MutableComponent base = Component.literal("Enchantments do not increase the sell value. Sell anyway for " +
                            EconomyCraft.formatMoney(total) +
                            "? ")
                    .withStyle(ChatFormatting.YELLOW);
            ClickEvent ev = ChatCompat.runCommandEvent("/sell confirm");
            if (ev != null) {
                player.sendSystemMessage(base.append(Component.literal("[CONFIRM]")
                        .withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev))));
            } else {
                player.sendSystemMessage(base);
                ChatCompat.sendRunCommandTellraw(player, "", "[CONFIRM]", "/sell confirm");
            }
            return 0;
        }

        return doSellHand(source, player, sc.manager(), hand, toSell, total);
    }

    private static int confirmSellHand(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        PendingHand pending = PENDING_HAND.remove(player.getUUID());
        if (pending == null || pending.expiresAt() < System.currentTimeMillis()) {
            source.sendFailure(Component.literal("No pending sale. Run /sell again.").withStyle(ChatFormatting.RED));
            return 0;
        }

        SellContext sc = validateSellable(ctx);
        if (sc == null) return 0;
        ItemStack hand = sc.hand();

        if (!hand.isEnchanted() || hand.getCount() < pending.amount()) {
            source.sendFailure(Component.literal("Held item changed. Run /sell again.").withStyle(ChatFormatting.RED));
            return 0;
        }

        Long total = safeMultiply(sc.unitSell(), pending.amount());
        if (total == null) {
            source.sendFailure(Component.literal("Sale amount is too large.").withStyle(ChatFormatting.RED));
            return 0;
        }

        return doSellHand(source, player, sc.manager(), hand, pending.amount(), total);
    }

    private static int doSellHand(CommandSourceStack source, ServerPlayer player, EconomyManager manager,
                                  ItemStack hand, int toSell, long total) {
        if (EconomyConfig.get().dailySellLimit > 0 && manager.tryRecordDailySell(player.getUUID(), total)) {
            return handleDailyLimitFailure(manager, player, source);
        }

        String itemName = hand.getHoverName().getString();
        hand.shrink(toSell);
        if (hand.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }

        manager.addMoney(player.getUUID(), total);
        player.sendSystemMessage(Component.literal("Successfully sold " + toSell + "x " + itemName +
                        " for " + EconomyCraft.formatMoney(total) + ".")
                .withStyle(ChatFormatting.GREEN));
        return toSell;
    }

    private static int previewSellAll(CommandContext<CommandSourceStack> ctx) {
        SellContext sc = validateSellable(ctx);
        if (sc == null) return 0;
        CommandSourceStack source = sc.source();
        ServerPlayer player = sc.player();
        ItemStack hand = sc.hand();
        PriceRegistry prices = sc.prices();

        int totalCount = SellService.countMatching(player, prices, sc.resolved().key(), false);
        if (totalCount <= 0) {
            source.sendFailure(Component.literal("This item cannot be sold.").withStyle(ChatFormatting.RED));
            return 0;
        }

        Long total = safeMultiply(sc.unitSell(), totalCount);
        if (total == null) {
            source.sendFailure(Component.literal("Sale amount is too large.").withStyle(ChatFormatting.RED));
            return 0;
        }

        IdentifierCompat.Id heldItemId = IdentifierCompat.wrap(BuiltInRegistries.ITEM.getKey(hand.getItem()));
        PENDING.put(player.getUUID(), new PendingSale(sc.resolved().key(), totalCount, total,
                System.currentTimeMillis() + CONFIRM_EXPIRY_MS, heldItemId));

        String itemName = hand.getHoverName().getString();
        MutableComponent base = Component.literal("This will sell " + totalCount + "x " + itemName +
                        " for " + EconomyCraft.formatMoney(total) + ". ")
                .withStyle(ChatFormatting.YELLOW);

        ClickEvent ev = ChatCompat.runCommandEvent("/sell all confirm");
        if (ev != null) {
            player.sendSystemMessage(base.append(Component.literal("[CONFIRM]")
                    .withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev))));
        } else {
            player.sendSystemMessage(base);
            ChatCompat.sendRunCommandTellraw(player, "", "[CONFIRM]", "/sell all confirm");
        }

        return totalCount;
    }

    private static int confirmSellAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        PendingSale pending = PENDING.get(player.getUUID());
        if (pending == null || pending.expiresAt() < System.currentTimeMillis()) {
            source.sendFailure(Component.literal("No pending sale. Run /sell all again.").withStyle(ChatFormatting.RED));
            PENDING.remove(player.getUUID());
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        PriceRegistry prices = manager.getPrices();

        ItemStack hand = player.getMainHandItem();
        ResolvedPrice current = prices.resolve(hand);
        if (current == null || !pending.key().equals(current.key())) {
            source.sendFailure(Component.literal("Held item changed. Run /sell all again.").withStyle(ChatFormatting.RED));
            PENDING.remove(player.getUUID());
            return 0;
        }

        IdentifierCompat.Id currentItemId = IdentifierCompat.wrap(BuiltInRegistries.ITEM.getKey(hand.getItem()));
        if (!currentItemId.equals(pending.heldItemId())) {
            source.sendFailure(Component.literal("Held item changed. Run /sell all again.").withStyle(ChatFormatting.RED));
            PENDING.remove(player.getUUID());
            return 0;
        }

        if (prices.isSellBlockedByDamage(hand)) {
            source.sendFailure(Component.literal("Damaged items cannot be sold.").withStyle(ChatFormatting.RED));
            PENDING.remove(player.getUUID());
            return 0;
        }

        if (prices.isSellBlockedByContents(hand)) {
            source.sendFailure(Component.literal("Items with contents cannot be sold.").withStyle(ChatFormatting.RED));
            PENDING.remove(player.getUUID());
            return 0;
        }

        int available = SellService.countMatching(player, prices, pending.key(), false);
        if (available < pending.count()) {
            source.sendFailure(Component.literal("Items changed. Run /sell all again.").withStyle(ChatFormatting.RED));
            PENDING.remove(player.getUUID());
            return 0;
        }

        if (EconomyConfig.get().dailySellLimit > 0 && manager.tryRecordDailySell(player.getUUID(), pending.total())) {
            return handleDailyLimitFailure(manager, player, source);
        }

        String itemName = hand.getHoverName().getString();
        SellService.removeMatching(player, prices, pending.key(), pending.count(), false);
        manager.addMoney(player.getUUID(), pending.total());

        Component msg = Component.literal("Successfully sold " + pending.count() + "x " +
                        itemName + " for " + EconomyCraft.formatMoney(pending.total()) + ".")
                .withStyle(ChatFormatting.GREEN);
        player.sendSystemMessage(msg);
        PENDING.remove(player.getUUID());
        return pending.count();
    }

    // =====================================================================
    // === /sell everything ================================================
    // =====================================================================

    private static int previewSellEverything(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        PriceRegistry prices = manager.getPrices();

        EverythingTotals totals = computeEverythingTotals(player, prices);
        if (totals.overflow()) {
            source.sendFailure(Component.literal("Sale amount is too large.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (totals.count() <= 0) {
            source.sendFailure(Component.literal("You have no sellable items in your inventory.").withStyle(ChatFormatting.RED));
            return 0;
        }

        PENDING_EVERYTHING.put(player.getUUID(),
                new PendingEverything(totals.count(), totals.total(), System.currentTimeMillis() + CONFIRM_EXPIRY_MS));

        MutableComponent base = Component.literal("This sells your entire inventory (" + totals.count() + " item" +
                        (totals.count() == 1 ? "" : "s") + ") for " + EconomyCraft.formatMoney(totals.total()) + " ")
                .withStyle(ChatFormatting.GOLD);

        ClickEvent ev = ChatCompat.runCommandEvent("/sell everything confirm");
        if (ev != null) {
            player.sendSystemMessage(base.append(Component.literal("[CONFIRM]")
                    .withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev))));
        } else {
            player.sendSystemMessage(base);
            ChatCompat.sendRunCommandTellraw(player, "", "[CONFIRM]", "/sell everything confirm");
        }

        return totals.count();
    }

    private static int confirmSellEverything(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        PendingEverything pending = PENDING_EVERYTHING.get(player.getUUID());
        if (pending == null || pending.expiresAt() < System.currentTimeMillis()) {
            source.sendFailure(Component.literal("No pending sale. Run /sell everything again.").withStyle(ChatFormatting.RED));
            PENDING_EVERYTHING.remove(player.getUUID());
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        PriceRegistry prices = manager.getPrices();

        EverythingTotals totals = computeEverythingTotals(player, prices);
        if (totals.overflow()) {
            source.sendFailure(Component.literal("Sale amount is too large.").withStyle(ChatFormatting.RED));
            PENDING_EVERYTHING.remove(player.getUUID());
            return 0;
        }
        if (totals.count() <= 0) {
            source.sendFailure(Component.literal("You have no sellable items in your inventory.").withStyle(ChatFormatting.RED));
            PENDING_EVERYTHING.remove(player.getUUID());
            return 0;
        }

        if (totals.count() != pending.count() || totals.total() != pending.total()) {
            source.sendFailure(Component.literal("Inventory changed. Run /sell everything again.").withStyle(ChatFormatting.RED));
            PENDING_EVERYTHING.remove(player.getUUID());
            return 0;
        }

        if (EconomyConfig.get().dailySellLimit > 0 && manager.tryRecordDailySell(player.getUUID(), totals.total())) {
            return handleDailyLimitFailure(manager, player, source);
        }

        removeAllSellable(player, prices);
        manager.addMoney(player.getUUID(), totals.total());

        Component msg = Component.literal("Successfully sold your entire inventory (" + totals.count() + " item" +
                        (totals.count() == 1 ? "" : "s") + ") for " + EconomyCraft.formatMoney(totals.total()) + ".")
                .withStyle(ChatFormatting.GREEN);
        player.sendSystemMessage(msg);
        PENDING_EVERYTHING.remove(player.getUUID());
        return totals.count();
    }

    /** Sums the value and count of every sellable stack in the swept inventory region. */
    private static EverythingTotals computeEverythingTotals(ServerPlayer player, PriceRegistry prices) {
        var inv = player.getInventory();
        long total = 0;
        int count = 0;

        for (int i = 0; i < SellService.MAIN_INVENTORY_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (SellService.sellableResolved(prices, stack) == null) continue;

            Long value = safeMultiply(prices.getUnitSell(stack), stack.getCount());
            Long sum = value == null ? null : safeAdd(total, value);
            if (sum == null) return new EverythingTotals(0, 0, true);
            total = sum;
            count += stack.getCount();
        }

        ItemStack offhand = player.getOffhandItem();
        if (SellService.sellableResolved(prices, offhand) != null) {
            Long value = safeMultiply(prices.getUnitSell(offhand), offhand.getCount());
            Long sum = value == null ? null : safeAdd(total, value);
            if (sum == null) return new EverythingTotals(0, 0, true);
            total = sum;
            count += offhand.getCount();
        }

        return new EverythingTotals(count, total, false);
    }

    private static void removeAllSellable(ServerPlayer player, PriceRegistry prices) {
        var inv = player.getInventory();
        for (int i = 0; i < SellService.MAIN_INVENTORY_SLOTS; i++) {
            if (SellService.sellableResolved(prices, inv.getItem(i)) != null) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (SellService.sellableResolved(prices, player.getOffhandItem()) != null) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private static Long safeMultiply(long value, int count) {
        try {
            return Math.multiplyExact(value, count);
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static Long safeAdd(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static ServerPlayer getPlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can use this command.").withStyle(ChatFormatting.RED));
            return null;
        }
    }

    private record PendingSale(IdentifierCompat.Id key, int count, long total, long expiresAt,
                               IdentifierCompat.Id heldItemId) {}

    private record PendingEverything(int count, long total, long expiresAt) {}

    private record PendingHand(int amount, long expiresAt) {}

    private record EverythingTotals(int count, long total, boolean overflow) {}

    private static int handleDailyLimitFailure(EconomyManager manager, ServerPlayer player, CommandSourceStack source) {
        long remaining = manager.getDailySellRemaining(player.getUUID());
        long limit = EconomyConfig.get().dailySellLimit;

        if (remaining <= 0) {
            source.sendFailure(Component.literal("Daily sell limit of " + EconomyCraft.formatMoney(limit) + " reached. Try again tomorrow.")
                    .withStyle(ChatFormatting.RED));
        } else {
            source.sendFailure(Component.literal("This sale exceeds the daily sell limit of " +
                            EconomyCraft.formatMoney(limit) + ". You can sell items worth " +
                            EconomyCraft.formatMoney(remaining) + " more today.")
                    .withStyle(ChatFormatting.RED));
        }
        return 0;
    }
}
