package com.reazip.economycraft.orders;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.ChatCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Shared logic for fulfilling an order request, fully or partially. Driven by the orders GUI,
 * whose confirm button fulfills as much of the request as the player currently holds.
 *
 * <p>Payment is proportional to the fraction fulfilled and is deducted from the order's
 * remaining amount and price, so a sequence of partial fulfillments pays out exactly the
 * original total and the order is removed once its outstanding amount reaches zero.
 */
public final class OrderFulfillment {
    private OrderFulfillment() {}

    public enum Status {
        OK, ORDER_GONE, OWN_ORDER, INVALID_AMOUNT, NOT_ENOUGH_ITEMS, REQUESTER_CANT_PAY
    }

    /**
     * @param given     how many items were actually handed over (0 on failure)
     * @param payout    money paid to the fulfiller, after tax (0 on failure)
     * @param remaining amount still outstanding on the order after this fulfillment
     * @param item      a copy of the requested item (for naming); EMPTY when the order is gone
     * @param requester the requesting player's id, or null when the order is gone
     */
    public record Result(Status status, int given, long payout, int remaining, ItemStack item, UUID requester) {
        public boolean success() {
            return status == Status.OK;
        }
    }

    /**
     * Fulfills up to {@code requestedAmount} of order {@code orderId} from {@code fulfiller}'s inventory.
     * Pass a non-positive {@code requestedAmount} to fulfill the entire outstanding amount.
     */
    public static Result fulfill(EconomyManager eco, ServerPlayer fulfiller, int orderId, int requestedAmount) {
        OrderManager orders = eco.getOrders();
        OrderRequest order = orders.getRequest(orderId);
        if (order == null || order.item == null || order.item.isEmpty()) {
            return new Result(Status.ORDER_GONE, 0, 0, 0, ItemStack.EMPTY, null);
        }
        if (fulfiller.getUUID().equals(order.requester)) {
            return new Result(Status.OWN_ORDER, 0, 0, order.amount, order.item.copy(), order.requester);
        }

        int give = requestedAmount <= 0 ? order.amount : Math.min(requestedAmount, order.amount);
        if (give <= 0) {
            return new Result(Status.INVALID_AMOUNT, 0, 0, order.amount, order.item.copy(), order.requester);
        }

        if (countHeld(fulfiller, order.item) < give) {
            return new Result(Status.NOT_ENOUGH_ITEMS, 0, 0, order.amount, order.item.copy(), order.requester);
        }

        long payment = order.amount <= 0 ? 0 : Math.round((double) order.price * give / order.amount);
        payment = Math.min(payment, order.price);

        ItemStack itemProto = order.item.copy();
        UUID requester = order.requester;

        if (!eco.removeMoney(requester, payment)) {
            return new Result(Status.REQUESTER_CANT_PAY, 0, 0, order.amount, itemProto, order.requester);
        }

        removeItems(fulfiller, itemProto, give);
        long tax = Math.round(payment * EconomyConfig.get().taxRate);
        long payout = payment - tax;
        eco.addMoney(fulfiller.getUUID(), payout);

        deliver(orders, requester, itemProto, give);

        order.amount -= give;
        order.price -= payment;
        int remaining = order.amount;
        if (remaining <= 0) {
            orders.removeRequest(order.id);
            remaining = 0;
        } else {
            orders.markChanged();
        }

        notifyRequester(eco.getServer(), requester, give, itemProto);
        return new Result(Status.OK, give, payout, remaining, itemProto, requester);
    }

    /** Total count of items matching the order's item type across the fulfiller's inventory. */
    public static int countHeld(ServerPlayer player, ItemStack proto) {
        if (proto == null || proto.isEmpty()) return 0;
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(proto.getItem())) total += s.getCount();
        }
        return total;
    }

    /** Net payout (after tax) for fulfilling {@code give} of {@code order}. */
    public static long payoutFor(OrderRequest order, int give) {
        if (order == null || order.amount <= 0 || give <= 0) return 0;
        long payment = Math.min(Math.round((double) order.price * give / order.amount), order.price);
        long tax = Math.round(payment * EconomyConfig.get().taxRate);
        return payment - tax;
    }

    private static void removeItems(ServerPlayer player, ItemStack proto, int amount) {
        int remaining = amount;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(proto.getItem())) {
                int take = Math.min(s.getCount(), remaining);
                s.shrink(take);
                remaining -= take;
                if (remaining <= 0) return;
            }
        }
    }

    private static void deliver(OrderManager orders, UUID requester, ItemStack proto, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int c = Math.min(proto.getMaxStackSize(), remaining);
            orders.addDelivery(requester, new ItemStack(proto.getItem(), c));
            remaining -= c;
        }
    }

    private static void notifyRequester(MinecraftServer server, UUID requester, int amount, ItemStack item) {
        ServerPlayer requesterPlayer = server.getPlayerList().getPlayer(requester);
        if (requesterPlayer == null) return;

        String itemName = item.getHoverName().getString();
        String prefix = amount + "x " + itemName + " of your request has been fulfilled: ";

        ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
        if (ev != null) {
            requesterPlayer.sendSystemMessage(Component.literal(prefix)
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("[Claim]")
                            .withStyle(s -> s.withUnderlined(true)
                                    .withColor(ChatFormatting.GREEN)
                                    .withClickEvent(ev))));
        } else {
            ChatCompat.sendRunCommandTellraw(requesterPlayer, prefix, "[Claim]", "/eco orders claim");
        }
    }
}
