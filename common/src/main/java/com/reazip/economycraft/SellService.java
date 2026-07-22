package com.reazip.economycraft;

import com.reazip.economycraft.PriceRegistry.PriceEntry;
import com.reazip.economycraft.PriceRegistry.ResolvedPrice;
import com.reazip.economycraft.orders.OrderFulfillment;
import com.reazip.economycraft.orders.OrderRequest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shared inventory-selling helpers used by the {@code /sell} command and the server shop.
 * Operates over the 36 main-inventory slots plus the offhand (armor is never touched).
 */
public final class SellService {
    private SellService() {}

    public static final int MAIN_INVENTORY_SLOTS = 36;

    /** A stack's resolved price if it is sellable (has a sell price and isn't blocked), else null. */
    @Nullable
    public static ResolvedPrice sellableResolved(PriceRegistry prices, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResolvedPrice rp = prices.resolve(stack);
        if (rp == null) return null;
        // A "components" entry only ever matches a stack whose id, damage, and every other data
        // component (including contents) is an exact match - that's the whole point of pricing a
        // specific filled shulker box or a specific enchanted/damaged item. The generic
        // damage/contents guards below exist to protect entries that DON'T know the item's exact
        // state, so they don't apply once we already have an exact match.
        if (rp.entry().customItem() == null) {
            if (prices.isSellBlockedByDamage(stack)) return null;
            if (prices.isSellBlockedByContents(stack)) return null;
        }
        if (prices.getUnitSell(stack) == null) return null;
        return rp;
    }

    public static boolean isMatching(PriceRegistry prices, ItemStack stack, PriceEntry expected, boolean excludeEnchanted) {
        if (stack == null || stack.isEmpty()) return false;
        if (excludeEnchanted && stack.isEnchanted()) return false;
        if (!prices.matches(stack, expected)) return false;
        // Generic entries don't know the item's exact state, so a damaged tool or filled container
        // can't sell at a generic price - a "components" entry only matches an exact state already.
        if (expected.customItem() == null) {
            if (prices.isSellBlockedByDamage(stack)) return false;
            if (prices.isSellBlockedByContents(stack)) return false;
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

    /**
     * Routes {@code amount} of {@code proto} to open orders paying more than {@code serverUnitSell},
     * best price first; the rest is left in {@link SaleSplit#serverRemaining()}. Order sales don't
     * count against the daily sell limit. Sweeps the fulfiller's whole inventory for matching
     * stacks - correct for "sell all"/"sell everything", which mean exactly that.
     */
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

    /**
     * Same as {@link #sellWithRouting}, but consumes only from {@code hand} - a live reference to
     * exactly the stack the player is selling - instead of sweeping the whole inventory. Used by
     * plain {@code /sell}, which only ever means "sell what's in my hand," never other matching
     * stacks elsewhere in the inventory.
     */
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
