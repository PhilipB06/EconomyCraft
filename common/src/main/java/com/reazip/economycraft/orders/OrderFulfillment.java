package com.reazip.economycraft.orders;

import com.mojang.logging.LogUtils;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.EconomySources;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.SellService;
import com.reazip.economycraft.api.v1.BalanceMutationResult;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.ExpirationUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

public final class OrderFulfillment {
    private OrderFulfillment() {}

    private static final Logger LOGGER = LogUtils.getLogger();

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

    private record PaymentOutcome(boolean success, long payout, Status failureStatus) {}

    public static OrderRequest createEscrowedRequest(EconomyManager eco, UUID requester, ItemStack item, int amount, long price) {
        String detail = EconomyCraft.describeItem(amount, item.getHoverName().getString());
        if (!eco.removeMoney(requester, price, EconomySources.ORDER_ESCROW_HOLD, detail).successful()) {
            return null;
        }

        OrderRequest request = new OrderRequest();
        request.requester = requester;
        request.price = price;
        request.item = item;
        request.amount = amount;
        request.escrow = price;
        request.createdAt = System.currentTimeMillis();
        request.expiresAt = ExpirationUtil.expiresAt(request.createdAt, EconomyConfig.get().orderExpirationHours);
        eco.getOrders().addRequest(request);
        return request;
    }

    public static Result fulfill(EconomyManager eco, ServerPlayer fulfiller, int orderId, int requestedAmount) {
        return fulfill(eco, fulfiller, orderId, requestedAmount, false);
    }

    public static Result fulfill(EconomyManager eco, ServerPlayer fulfiller, int orderId, int requestedAmount, boolean excludeArmor) {
        OrderManager orders = eco.getOrders();
        OrderRequest peek = orders.getRequest(orderId);
        if (peek == null || peek.item == null || peek.item.isEmpty()) {
            return new Result(Status.ORDER_GONE, 0, 0, 0, ItemStack.EMPTY, null);
        }
        if (fulfiller.getUUID().equals(peek.requester)) {
            return new Result(Status.OWN_ORDER, 0, 0, peek.amount, peek.item.copy(), peek.requester);
        }

        ItemStack itemProto = peek.item.copy();
        int held = countHeld(fulfiller, itemProto, excludeArmor);

        OrderManager.ClaimResult claim = orders.claim(orderId, requestedAmount, held, false);
        return applyFulfillment(eco, orders, claim, peek.requester, fulfiller.getUUID(), itemProto,
                give -> removeItems(fulfiller, itemProto, give, excludeArmor));
    }

    public static Result fulfillExact(EconomyManager eco, ServerPlayer fulfiller, int orderId, int requestedAmount, ItemStack sourceStack) {
        OrderManager orders = eco.getOrders();
        OrderRequest peek = orders.getRequest(orderId);
        if (peek == null || peek.item == null || peek.item.isEmpty()) {
            return new Result(Status.ORDER_GONE, 0, 0, 0, ItemStack.EMPTY, null);
        }
        if (fulfiller.getUUID().equals(peek.requester)) {
            return new Result(Status.OWN_ORDER, 0, 0, peek.amount, peek.item.copy(), peek.requester);
        }
        if (sourceStack == null || !ItemStack.isSameItemSameComponents(sourceStack, peek.item)) {
            return new Result(Status.NOT_ENOUGH_ITEMS, 0, 0, peek.amount, peek.item.copy(), peek.requester);
        }

        ItemStack itemProto = peek.item.copy();
        OrderManager.ClaimResult claim = orders.claim(orderId, requestedAmount, sourceStack.getCount(), true);
        return applyFulfillment(eco, orders, claim, peek.requester, fulfiller.getUUID(), itemProto, sourceStack::shrink);
    }

    private static Result applyFulfillment(EconomyManager eco, OrderManager orders, OrderManager.ClaimResult claim,
                                            UUID requester, UUID fulfillerId, ItemStack itemProto, IntConsumer takeItems) {
        switch (claim.status()) {
            case ORDER_GONE -> {
                return new Result(Status.ORDER_GONE, 0, 0, 0, ItemStack.EMPTY, null);
            }
            case INVALID_AMOUNT -> {
                return new Result(Status.INVALID_AMOUNT, 0, 0, claim.order().amount, itemProto, requester);
            }
            case FULL_AMOUNT_REQUIRED -> {
                return new Result(Status.FULL_AMOUNT_REQUIRED, 0, 0, claim.order().amount, itemProto, requester);
            }
            case NOT_ENOUGH_ITEMS -> {
                return new Result(Status.NOT_ENOUGH_ITEMS, 0, 0, claim.order().amount, itemProto, requester);
            }
            default -> {}
        }

        String detail = EconomyCraft.describeItem(claim.given(), itemProto.getHoverName().getString());
        PaymentOutcome outcome = settleOrderPayment(eco, requester, fulfillerId, claim.payment(), claim.escrowUsed(), detail);
        if (!outcome.success()) {
            orders.rollbackClaim(claim.order(), claim.given(), claim.payment(), claim.escrowUsed(), claim.exhausted());
            return new Result(outcome.failureStatus(), 0, 0, claim.order().amount, itemProto, requester);
        }

        takeItems.accept(claim.given());
        deliver(orders, requester, itemProto, claim.given());
        notifyRequester(eco.getServer(), requester, claim.given(), itemProto);
        orders.markChanged();

        int remaining = claim.exhausted() ? 0 : claim.order().amount;
        return new Result(Status.OK, claim.given(), outcome.payout(), remaining, itemProto, requester);
    }

    private static PaymentOutcome settleOrderPayment(EconomyManager eco, UUID requester, UUID fulfillerId, long payment,
                                                       long escrowUsed, String detail) {
        long tax = Math.round(payment * EconomyConfig.get().taxRate);
        long payout = payment - tax;
        long shortfall = payment - escrowUsed;

        if (shortfall > 0) {
            var transfer = eco.transferMoney(requester, fulfillerId, shortfall, payout, EconomySources.ORDER_FULFILLMENT, detail);
            if (!transfer.successful()) {
                Status status = transfer.status() == com.reazip.economycraft.api.v1.BalanceMutationStatus.MAX_BALANCE_EXCEEDED
                        ? Status.FULFILLER_CANT_RECEIVE : Status.REQUESTER_CANT_PAY;
                return new PaymentOutcome(false, 0, status);
            }
        } else if (payout > 0) {
            var credit = eco.addMoney(fulfillerId, payout, EconomySources.ORDER_FULFILLMENT, detail);
            if (!credit.successful()) {
                return new PaymentOutcome(false, 0, Status.FULFILLER_CANT_RECEIVE);
            }
        }
        return new PaymentOutcome(true, payout, null);
    }

    public static CancelStatus cancel(EconomyManager eco, UUID requester, int orderId) {
        OrderManager orders = eco.getOrders();
        OrderRequest peek = orders.getRequest(orderId);
        if (peek == null) return CancelStatus.ORDER_GONE;
        if (!peek.requester.equals(requester)) return CancelStatus.NOT_OWNER;

        OrderRequest order = orders.removeRequest(orderId);
        if (order == null) return CancelStatus.ORDER_GONE;

        var refund = refundEscrow(eco, orders, order);
        if (refund != null && !refund.successful()) return CancelStatus.REFUND_FAILED;

        return CancelStatus.OK;
    }

    public static void expireOverdue(EconomyManager eco) {
        OrderManager orders = eco.getOrders();
        long now = System.currentTimeMillis();
        boolean anyExpired = false;
        for (OrderRequest snapshot : orders.getRequests()) {
            if (!ExpirationUtil.isExpired(snapshot.expiresAt, now)) continue;

            OrderRequest order = orders.removeRequest(snapshot.id, false);
            if (order == null) continue;
            anyExpired = true;

            long refund = order.escrow;
            var result = refundEscrow(eco, orders, order);
            if (result != null && !result.successful()) {
                LOGGER.warn("[EconomyCraft] Expired order {} escrow refund of {} to {} failed ({}); will retry next sweep",
                        order.id, refund, order.requester, result.status());
                continue;
            }

            notifyExpired(eco, order, refund);
        }
        if (anyExpired) orders.save();
        eco.getNotifications().flush();
    }

    private static BalanceMutationResult refundEscrow(EconomyManager eco, OrderManager orders, OrderRequest order) {
        if (order.escrow <= 0) return null;
        String detail = EconomyCraft.describeItem(order.amount, order.item.getHoverName().getString());
        var refund = eco.addMoney(order.requester, order.escrow, EconomySources.ORDER_ESCROW_REFUND, detail);
        if (refund.successful()) {
            order.escrow = 0;
        } else {
            orders.restoreRequest(order);
        }
        return refund;
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
            if (orderPrice == null || !orderPrice.key().equals(protoPrice.key())) continue;

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
        long payment = OrderManager.partialPayment(order, give);
        long tax = Math.round(payment * EconomyConfig.get().taxRate);
        return payment - tax;
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
