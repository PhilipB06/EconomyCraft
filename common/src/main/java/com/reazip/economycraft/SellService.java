package com.reazip.economycraft;

import com.reazip.economycraft.PriceRegistry.ResolvedPrice;
import com.reazip.economycraft.util.IdentifierCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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
        if (prices.isSellBlockedByDamage(stack)) return null;
        if (prices.isSellBlockedByContents(stack)) return null;
        ResolvedPrice rp = prices.resolve(stack);
        if (rp == null || prices.getUnitSell(stack) == null) return null;
        return rp;
    }

    public static boolean isMatching(PriceRegistry prices, ItemStack stack, IdentifierCompat.Id key, boolean excludeEnchanted) {
        if (excludeEnchanted && stack != null && stack.isEnchanted()) return false;
        ResolvedPrice rp = sellableResolved(prices, stack);
        return rp != null && key.equals(rp.key());
    }

    public static int countMatching(ServerPlayer player, PriceRegistry prices, IdentifierCompat.Id key, boolean excludeEnchanted) {
        var inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < MAIN_INVENTORY_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (isMatching(prices, stack, key, excludeEnchanted)) total += stack.getCount();
        }
        ItemStack offhand = player.getOffhandItem();
        if (isMatching(prices, offhand, key, excludeEnchanted)) total += offhand.getCount();
        return total;
    }

    public static void removeMatching(ServerPlayer player, PriceRegistry prices, IdentifierCompat.Id key, int toRemove, boolean excludeEnchanted) {
        var inv = player.getInventory();
        int remaining = toRemove;
        for (int i = 0; i < MAIN_INVENTORY_SLOTS && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            remaining = drain(prices, stack, key, remaining, excludeEnchanted);
            if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
        if (remaining > 0) {
            ItemStack offhand = player.getOffhandItem();
            remaining = drain(prices, offhand, key, remaining, excludeEnchanted);
            if (offhand.isEmpty()) player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private static int drain(PriceRegistry prices, ItemStack stack, IdentifierCompat.Id key, int remaining, boolean excludeEnchanted) {
        if (remaining <= 0 || !isMatching(prices, stack, key, excludeEnchanted)) return remaining;
        int remove = Math.min(remaining, stack.getCount());
        stack.shrink(remove);
        return remaining - remove;
    }
}
