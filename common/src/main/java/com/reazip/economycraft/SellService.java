package com.reazip.economycraft;

import com.reazip.economycraft.PriceRegistry.PriceEntry;
import com.reazip.economycraft.orders.OrderFulfillment;
import com.reazip.economycraft.orders.OrderRequest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public final class SellService {
    private SellService() {}

    @Nullable
    public static Long sellableUnitSell(PriceRegistry prices, ItemStack stack) {
        return sellableUnitSell(prices, stack, true);
    }

    @Nullable
    public static Long sellableUnitSellIgnoringContents(PriceRegistry prices, ItemStack stack) {
        return sellableUnitSell(prices, stack, false);
    }

    @Nullable
    private static Long sellableUnitSell(PriceRegistry prices, ItemStack stack, boolean checkContents) {
        if (stack == null || stack.isEmpty()) return null;
        PriceEntry entry = prices.resolve(stack);
        if (entry == null) return null;
        if (entry.customItem() == null && checkContents && prices.isSellBlockedByContents(stack)) return null;
        return prices.getUnitSell(stack, entry);
    }

    public static boolean isShulkerBox(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static boolean isMatching(PriceRegistry prices, ItemStack stack, PriceEntry expected, boolean excludeEnchanted) {
        if (stack == null || stack.isEmpty()) return false;
        if (excludeEnchanted && stack.isEnchanted()) return false;
        if (!prices.matches(stack, expected)) return false;
        if (expected.customItem() == null) {
            if (prices.isDamaged(stack)) return false;
            return !prices.isSellBlockedByContents(stack);
        }
        return true;
    }

    public static int countMatching(ServerPlayer player, PriceRegistry prices, PriceEntry expected, boolean excludeEnchanted) {
        return count(player, stack -> isMatching(prices, stack, expected, excludeEnchanted));
    }

    public static void removeMatching(ServerPlayer player, PriceRegistry prices, PriceEntry expected, int toRemove, boolean excludeEnchanted) {
        remove(player, stack -> isMatching(prices, stack, expected, excludeEnchanted), toRemove);
    }

    public static int countHeld(ServerPlayer player, ItemStack proto) {
        if (proto == null || proto.isEmpty()) return 0;
        return count(player, stack -> ItemStack.isSameItemSameComponents(stack, proto));
    }

    public static void removeHeld(ServerPlayer player, ItemStack proto, int toRemove) {
        remove(player, stack -> ItemStack.isSameItemSameComponents(stack, proto), toRemove);
    }

    private static int count(ServerPlayer player, Predicate<ItemStack> matches) {
        var inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (matches.test(stack)) total += stack.getCount();
        }
        ItemStack offhand = player.getOffhandItem();
        if (matches.test(offhand)) total += offhand.getCount();
        return total;
    }

    private static void remove(ServerPlayer player, Predicate<ItemStack> matches, int toRemove) {
        var inv = player.getInventory();
        int remaining = toRemove;
        for (int i = 0; i < Inventory.INVENTORY_SIZE && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            remaining = drain(stack, matches, remaining);
            if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
        if (remaining > 0) {
            ItemStack offhand = player.getOffhandItem();
            drain(offhand, matches, remaining);
            if (offhand.isEmpty()) player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private static int drain(ItemStack stack, Predicate<ItemStack> matches, int remaining) {
        if (remaining <= 0 || !matches.test(stack)) return remaining;
        int remove = Math.min(remaining, stack.getCount());
        stack.shrink(remove);
        return remaining - remove;
    }

    public static SaleSplit sellHandWithRouting(EconomyManager eco, ServerPlayer seller, ItemStack hand,
                                                 int amount, long serverUnitSell) {
        List<OrderRequest> orders = OrderFulfillment.findBetterOrders(eco, hand, seller.getUUID(), serverUnitSell);

        int given = 0;
        long payout = 0;
        for (OrderRequest order : orders) {
            if (given >= amount) break;
            OrderFulfillment.Result r = OrderFulfillment.fulfillExact(eco, seller, order.id, amount - given, hand);
            if (r.success()) {
                given += r.given();
                payout += r.payout();
            }
        }

        return new SaleSplit(given, payout, amount - given);
    }

    public record SaleSplit(int orderGiven, long orderPayout, int serverRemaining) {}
}
