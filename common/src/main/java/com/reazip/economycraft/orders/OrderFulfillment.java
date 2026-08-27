package com.reazip.economycraft.orders;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.EconomySources;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.SellService;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.ExpirationUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OrderFulfillment {
    private OrderFulfillment() {}

    public enum Status {
        OK, ORDER_GONE, OWN_ORDER, INVALID_AMOUNT, NOT_ENOUGH_ITEMS, REQUESTER_CANT_PAY,
        FULFILLER_CANT_RECEIVE, FULL_AMOUNT_REQUIRED
    }

    public record Result(Status status, int given, long payout, int remaining, ItemStack item, UUID requester) {
        public boolean success() {
            return status == Status.OK;
        }
    }

    public enum CancelStatus {
        OK, ORDER_GONE, NOT_OWNER, REFUND_FAILED
    }

    private record PaymentOutcome(boolean success, long payout, long escrowUsed, Status failureStatus) {}

    public static Result fulfill(EconomyManager eco, ServerPlayer fulfiller, int orderId, int requestedAmount) {
        return fulfill(eco, fulfiller, orderId, requestedAmount, false);
    }

    public static Result fulfill(EconomyManager eco, ServerPlayer fulfiller, int orderId, int requestedAmount, boolean excludeArmor) {
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
        if (requiresCompleteFulfillment(order) && give < order.amount) {
            return new Result(Status.FULL_AMOUNT_REQUIRED, 0, 0, order.amount, order.item.copy(), order.requester);
        }

        if (countHeld(fulfiller, order.item, excludeArmor) < give) {
            return new Result(Status.NOT_ENOUGH_ITEMS, 0, 0, order.amount, order.item.copy(), order.requester);
        }

        long payment = order.amount <= 0 ? 0 : Math.round((double) order.price * give / order.amount);
        payment = Math.min(payment, order.price);

        ItemStack itemProto = order.item.copy();
        UUID requester = order.requester;

        PaymentOutcome outcome = settleOrderPayment(eco, order, fulfiller.getUUID(), payment);
        if (!outcome.success()) {
            return new Result(outcome.failureStatus(), 0, 0, order.amount, itemProto, order.requester);
        }

        removeItems(fulfiller, itemProto, give, excludeArmor);

        deliver(orders, requester, itemProto, give);

        order.amount -= give;
        order.price -= payment;
        order.escrow -= outcome.escrowUsed();
        int remaining = order.amount;
        if (remaining <= 0) {
            orders.removeRequest(order.id);
            remaining = 0;
        } else {
            orders.markChanged();
        }

        notifyRequester(eco.getServer(), requester, give, itemProto);
        return new Result(Status.OK, give, outcome.payout(), remaining, itemProto, requester);
    }

    public static Result fulfillExact(EconomyManager eco, ServerPlayer fulfiller, int orderId, int requestedAmount, ItemStack sourceStack) {
        OrderManager orders = eco.getOrders();
        OrderRequest order = orders.getRequest(orderId);
        if (order == null || order.item == null || order.item.isEmpty()) {
            return new Result(Status.ORDER_GONE, 0, 0, 0, ItemStack.EMPTY, null);
        }
        if (fulfiller.getUUID().equals(order.requester)) {
            return new Result(Status.OWN_ORDER, 0, 0, order.amount, order.item.copy(), order.requester);
        }
        if (sourceStack == null || !ItemStack.isSameItemSameComponents(sourceStack, order.item)) {
            return new Result(Status.NOT_ENOUGH_ITEMS, 0, 0, order.amount, order.item.copy(), order.requester);
        }

        int give = requestedAmount <= 0 ? order.amount : Math.min(requestedAmount, order.amount);
        give = Math.min(give, sourceStack.getCount());
        if (give <= 0) {
            return new Result(Status.INVALID_AMOUNT, 0, 0, order.amount, order.item.copy(), order.requester);
        }
        if (requiresCompleteFulfillment(order) && give < order.amount) {
            return new Result(Status.FULL_AMOUNT_REQUIRED, 0, 0, order.amount, order.item.copy(), order.requester);
        }

        long payment = order.amount <= 0 ? 0 : Math.round((double) order.price * give / order.amount);
        payment = Math.min(payment, order.price);

        ItemStack itemProto = order.item.copy();
        UUID requester = order.requester;

        PaymentOutcome outcome = settleOrderPayment(eco, order, fulfiller.getUUID(), payment);
        if (!outcome.success()) {
            return new Result(outcome.failureStatus(), 0, 0, order.amount, itemProto, order.requester);
        }

        sourceStack.shrink(give);

        deliver(orders, requester, itemProto, give);

        order.amount -= give;
        order.price -= payment;
        order.escrow -= outcome.escrowUsed();
        int remaining = order.amount;
        if (remaining <= 0) {
            orders.removeRequest(order.id);
            remaining = 0;
        } else {
            orders.markChanged();
        }

        notifyRequester(eco.getServer(), requester, give, itemProto);
        return new Result(Status.OK, give, outcome.payout(), remaining, itemProto, requester);
    }

    private static PaymentOutcome settleOrderPayment(EconomyManager eco, OrderRequest order, UUID fulfillerId, long payment) {
        long tax = Math.round(payment * EconomyConfig.get().taxRate);
        long payout = payment - tax;
        long escrowUsed = Math.min(payment, Math.max(0, order.escrow));
        long shortfall = payment - escrowUsed;

        if (shortfall > 0) {
            var transfer = eco.transferMoney(order.requester, fulfillerId, shortfall, payout, EconomySources.ORDER_FULFILLMENT);
            if (!transfer.successful()) {
                Status status = transfer.status() == com.reazip.economycraft.api.v1.BalanceMutationStatus.MAX_BALANCE_EXCEEDED
                        ? Status.FULFILLER_CANT_RECEIVE : Status.REQUESTER_CANT_PAY;
                return new PaymentOutcome(false, 0, 0, status);
            }
        } else if (payout > 0) {
            var credit = eco.addMoney(fulfillerId, payout, EconomySources.ORDER_FULFILLMENT);
            if (!credit.successful()) {
                return new PaymentOutcome(false, 0, 0, Status.FULFILLER_CANT_RECEIVE);
            }
        }
        return new PaymentOutcome(true, payout, escrowUsed, null);
    }

    public static CancelStatus cancel(EconomyManager eco, UUID requester, int orderId) {
        OrderManager orders = eco.getOrders();
        OrderRequest order = orders.getRequest(orderId);
        if (order == null) return CancelStatus.ORDER_GONE;
        if (!order.requester.equals(requester)) return CancelStatus.NOT_OWNER;

        if (order.escrow > 0) {
            var refund = eco.addMoney(order.requester, order.escrow, EconomySources.ORDER_ESCROW_REFUND);
            if (!refund.successful()) return CancelStatus.REFUND_FAILED;
            order.escrow = 0;
        }

        orders.removeRequest(orderId);
        return CancelStatus.OK;
    }

    public static void expireOverdue(EconomyManager eco) {
        OrderManager orders = eco.getOrders();
        long now = System.currentTimeMillis();
        for (OrderRequest order : orders.getRequests()) {
            if (!ExpirationUtil.isExpired(order.expiresAt, now)) continue;

            long refund = order.escrow;
            if (refund > 0) {
                var result = eco.addMoney(order.requester, refund, EconomySources.ORDER_ESCROW_REFUND);
                if (!result.successful()) continue;
                order.escrow = 0;
            }

            orders.removeRequest(order.id);
            notifyExpired(eco, order, refund);
        }
    }

    private static void notifyExpired(EconomyManager eco, OrderRequest order, long refund) {
        String itemName = order.item.getHoverName().getString();
        String message = "Your order for " + order.amount + "x " + itemName + " expired"
                + (refund > 0 ? " and " + EconomyCraft.formatMoney(refund) + " was refunded." : ".");
        eco.getNotifications().notify(order.requester, message);
    }

    public static List<OrderRequest> findBetterOrders(EconomyManager eco, ItemStack proto, UUID seller, long serverUnitSell) {
        PriceRegistry prices = eco.getPrices();
        PriceRegistry.PriceEntry protoPrice = prices.resolve(proto);
        if (protoPrice == null) return List.of();

        List<OrderRequest> out = new ArrayList<>();
        for (OrderRequest order : eco.getOrders().getRequests()) {
            if (order.amount <= 0 || order.item == null || order.item.isEmpty()) continue;
            if (!order.item.is(proto.getItem())) continue;
            if (seller.equals(order.requester)) continue;

            PriceRegistry.PriceEntry orderPrice = prices.resolve(order.item);
            if (orderPrice == null || orderPrice != protoPrice) continue;

            if (netRatePerUnit(order) > serverUnitSell) out.add(order);
        }
        out.sort((a, b) -> Double.compare(netRatePerUnit(b), netRatePerUnit(a)));
        return out;
    }

    public static int countHeld(ServerPlayer player, ItemStack proto) {
        return countHeld(player, proto, false);
    }

    private static int countHeld(ServerPlayer player, ItemStack proto, boolean excludeArmor) {
        if (proto == null || proto.isEmpty()) return 0;
        int total = 0;
        var inv = player.getInventory();
        int limit = excludeArmor ? SellService.MAIN_INVENTORY_SLOTS : inv.getContainerSize();
        for (int i = 0; i < limit; i++) {
            ItemStack s = inv.getItem(i);
            if (ItemStack.isSameItemSameComponents(s, proto)) total += s.getCount();
        }
        if (excludeArmor) {
            ItemStack offhand = player.getOffhandItem();
            if (ItemStack.isSameItemSameComponents(offhand, proto)) total += offhand.getCount();
        }
        return total;
    }

    public static long payoutFor(OrderRequest order, int give) {
        if (order == null || order.amount <= 0 || give <= 0) return 0;
        long payment = Math.min(Math.round((double) order.price * give / order.amount), order.price);
        long tax = Math.round(payment * EconomyConfig.get().taxRate);
        return payment - tax;
    }

    public static long rewardPerItem(long reward, int amount) {
        return amount <= 0 ? 0 : Math.round((double) reward / amount);
    }

    public static boolean requiresCompleteFulfillment(OrderRequest order) {
        return order != null && order.amount > 1 && rewardPerItem(order.price, order.amount) == 0;
    }

    private static double netRatePerUnit(OrderRequest order) {
        if (order == null || order.amount <= 0) return 0;
        return (order.price / (double) order.amount) * (1.0 - EconomyConfig.get().taxRate);
    }

    private static void removeItems(ServerPlayer player, ItemStack proto, int amount, boolean excludeArmor) {
        int remaining = amount;
        var inv = player.getInventory();
        int limit = excludeArmor ? SellService.MAIN_INVENTORY_SLOTS : inv.getContainerSize();
        for (int i = 0; i < limit && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (ItemStack.isSameItemSameComponents(s, proto)) {
                int take = Math.min(s.getCount(), remaining);
                s.shrink(take);
                remaining -= take;
            }
        }
        if (excludeArmor && remaining > 0) {
            ItemStack offhand = player.getOffhandItem();
            if (ItemStack.isSameItemSameComponents(offhand, proto)) {
                int take = Math.min(offhand.getCount(), remaining);
                offhand.shrink(take);
            }
        }
    }

    private static void deliver(OrderManager orders, UUID requester, ItemStack proto, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int c = Math.min(proto.getMaxStackSize(), remaining);
            orders.addDelivery(requester, proto.copyWithCount(c));
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
