package com.reazip.economycraft;

import com.reazip.economycraft.PriceRegistry.PriceEntry;
import com.reazip.economycraft.orders.OrderFulfillment;
import com.reazip.economycraft.orders.OrderRequest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SellService {
    private SellService() {}

    public static final int MAIN_INVENTORY_SLOTS = 36;

    @Nullable
    public static PriceEntry sellableResolved(PriceRegistry prices, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        PriceEntry entry = prices.resolve(stack);
        if (entry == null) return null;
        if (entry.customItem() == null) {
            if (prices.isSellBlockedByDamage(stack)) return null;
            if (prices.isSellBlockedByContents(stack)) return null;
        }
        if (prices.getUnitSell(stack) == null) return null;
        return entry;
    }

    private static boolean isMatching(PriceRegistry prices, ItemStack stack, PriceEntry expected, boolean excludeEnchanted) {
        if (stack == null || stack.isEmpty()) return false;
        if (excludeEnchanted && stack.isEnchanted()) return false;
        if (!prices.matches(stack, expected)) return false;
        if (expected.customItem() == null) {
            if (prices.isSellBlockedByDamage(stack)) return false;
            return !prices.isSellBlockedByContents(stack);
        }
        return true;
    }

    public static int countMatching(ServerPlayer player, PriceRegistry prices, PriceEntry expected, boolean excludeEnchanted) {
        var inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < MAIN_INVENTORY_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (isMatching(prices, stack, expected, excludeEnchanted)) total += stack.getCount();
        }
        ItemStack offhand = player.getOffhandItem();
        if (isMatching(prices, offhand, expected, excludeEnchanted)) total += offhand.getCount();
        return total;
    }

    public static void removeMatching(ServerPlayer player, PriceRegistry prices, PriceEntry expected, int toRemove, boolean excludeEnchanted) {
        var inv = player.getInventory();
        int remaining = toRemove;
        for (int i = 0; i < MAIN_INVENTORY_SLOTS && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            remaining = drain(prices, stack, expected, remaining, excludeEnchanted);
            if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
        if (remaining > 0) {
            ItemStack offhand = player.getOffhandItem();
            remaining = drain(prices, offhand, expected, remaining, excludeEnchanted);
            if (offhand.isEmpty()) player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private static int drain(PriceRegistry prices, ItemStack stack, PriceEntry expected, int remaining, boolean excludeEnchanted) {
        if (remaining <= 0 || !isMatching(prices, stack, expected, excludeEnchanted)) return remaining;
        int remove = Math.min(remaining, stack.getCount());
        stack.shrink(remove);
        return remaining - remove;
    }

    public static SaleSplit sellWithRouting(EconomyManager eco, ServerPlayer seller, ItemStack proto,
                                             int amount, long serverUnitSell) {
        List<OrderRequest> orders = OrderFulfillment.findBetterOrders(eco, proto, seller.getUUID(), serverUnitSell);

        int given = 0;
        long payout = 0;
        for (OrderRequest order : orders) {
            if (given >= amount) break;
            OrderFulfillment.Result r = OrderFulfillment.fulfill(eco, seller, order.id, amount - given, true);
            if (r.success()) {
                given += r.given();
                payout += r.payout();
            }
        }

        return new SaleSplit(given, payout, amount - given);
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
