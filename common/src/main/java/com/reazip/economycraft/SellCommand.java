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
    private static final long CONFIRM_EXPIRY_MS = 20_000L;
    private static final int MAIN_INVENTORY_SLOTS = 36;

    private SellCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return literal("sell")
                .then(literal("all")
                        .executes(SellCommand::previewSellAll)
                        .then(literal("confirm").executes(SellCommand::confirmSellAll)))
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
        EconomyManager manager = sc.manager();

        int available = hand.getCount();
        int toSell = amount < 0 ? available : amount;
        if (toSell < 1 || toSell > available) {
            source.sendFailure(Component.literal("Invalid amount.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String itemName = hand.getHoverName().getString();
        Long total = safeMultiply(sc.unitSell(), toSell);
        if (total == null) {
            source.sendFailure(Component.literal("Sale amount is too large.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (EconomyConfig.get().dailySellLimit > 0 && manager.tryRecordDailySell(player.getUUID(), total)) {
            return handleDailyLimitFailure(manager, player, source);
        }

        hand.shrink(toSell);
        if (hand.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }

        manager.addMoney(player.getUUID(), total);
        Component msg = Component.literal("Successfully sold " + toSell + "x " + itemName +
                        " for " + EconomyCraft.formatMoney(total) + ".")
                .withStyle(ChatFormatting.GREEN);
        player.sendSystemMessage(msg);
        return toSell;
    }

    private static int previewSellAll(CommandContext<CommandSourceStack> ctx) {
        SellContext sc = validateSellable(ctx);
        if (sc == null) return 0;
        CommandSourceStack source = sc.source();
        ServerPlayer player = sc.player();
        ItemStack hand = sc.hand();
        PriceRegistry prices = sc.prices();

        int totalCount = countMatchingSellable(player, prices, sc.resolved().key());
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

        int available = countMatchingSellable(player, prices, pending.key());
        if (available < pending.count()) {
            source.sendFailure(Component.literal("Items changed. Run /sell all again.").withStyle(ChatFormatting.RED));
            PENDING.remove(player.getUUID());
            return 0;
        }

        if (EconomyConfig.get().dailySellLimit > 0 && manager.tryRecordDailySell(player.getUUID(), pending.total())) {
            return handleDailyLimitFailure(manager, player, source);
        }

        String itemName = hand.getHoverName().getString();
        removeMatching(player, prices, pending.key(), pending.count());
        manager.addMoney(player.getUUID(), pending.total());

        Component msg = Component.literal("Successfully sold " + pending.count() + "x " +
                        itemName + " for " + EconomyCraft.formatMoney(pending.total()) + ".")
                .withStyle(ChatFormatting.GREEN);
        player.sendSystemMessage(msg);
        PENDING.remove(player.getUUID());
        return pending.count();
    }

    private static int countMatchingSellable(ServerPlayer player, PriceRegistry prices, IdentifierCompat.Id key) {
        var inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < MAIN_INVENTORY_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (isMatchingSellable(prices, stack, key)) {
                total += stack.getCount();
            }
        }

        ItemStack offhand = player.getOffhandItem();
        if (isMatchingSellable(prices, offhand, key)) {
            total += offhand.getCount();
        }
        return total;
    }

    private static void removeMatching(ServerPlayer player, PriceRegistry prices, IdentifierCompat.Id key, int toRemove) {
        var inv = player.getInventory();
        int remaining = toRemove;
        for (int i = 0; i < MAIN_INVENTORY_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            remaining = drainStack(prices, stack, key, remaining);
            if (stack.isEmpty()) {
                inv.setItem(i, ItemStack.EMPTY);
            }
            if (remaining <= 0) return;
        }

        ItemStack offhand = player.getOffhandItem();
        remaining = drainStack(prices, offhand, key, remaining);
        if (offhand.isEmpty()) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private static int drainStack(PriceRegistry prices, ItemStack stack, IdentifierCompat.Id key, int remaining) {
        if (remaining <= 0) return 0;
        if (!isMatchingSellable(prices, stack, key)) return remaining;

        int remove = Math.min(remaining, stack.getCount());
        stack.shrink(remove);
        if (stack.isEmpty()) {
            stack.setCount(0);
        }
        return remaining - remove;
    }

    private static boolean isMatchingSellable(PriceRegistry prices, ItemStack stack, IdentifierCompat.Id key) {
        if (stack == null || stack.isEmpty()) return false;
        if (prices.isSellBlockedByDamage(stack)) return false;
        if (prices.isSellBlockedByContents(stack)) return false;
        ResolvedPrice rp = prices.resolve(stack);
        return rp != null && key.equals(rp.key()) && prices.getUnitSell(stack) != null;
    }

    private static Long safeMultiply(long value, int count) {
        try {
            return Math.multiplyExact(value, count);
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
