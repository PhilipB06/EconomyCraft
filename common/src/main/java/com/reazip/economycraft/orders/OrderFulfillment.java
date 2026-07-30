package com.reazip.economycraft.orders;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.SellService;
import com.reazip.economycraft.util.ChatCompat;
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
        OK, ORDER_GONE, OWN_ORDER, INVALID_AMOUNT, NOT_ENOUGH_ITEMS, REQUESTER_CANT_PAY
    }

    public record Result(Status status, int given, long payout, int remaining, ItemStack item, UUID requester) {
        public boolean success() {
            return status == Status.OK;
        }
    }

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

        if (countHeld(fulfiller, order.item, excludeArmor) < give) {
            return new Result(Status.NOT_ENOUGH_ITEMS, 0, 0, order.amount, order.item.copy(), order.requester);
        }

        long payment = order.amount <= 0 ? 0 : Math.round((double) order.price * give / order.amount);
        payment = Math.min(payment, order.price);

        ItemStack itemProto = order.item.copy();
        UUID requester = order.requester;

        if (!eco.removeMoney(requester, payment)) {
            return new Result(Status.REQUESTER_CANT_PAY, 0, 0, order.amount, itemProto, order.requester);
        }

        removeItems(fulfiller, itemProto, give, excludeArmor);
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

        long payment = order.amount <= 0 ? 0 : Math.round((double) order.price * give / order.amount);
        payment = Math.min(payment, order.price);

        ItemStack itemProto = order.item.copy();
        UUID requester = order.requester;

        if (!eco.removeMoney(requester, payment)) {
            return new Result(Status.REQUESTER_CANT_PAY, 0, 0, order.amount, itemProto, order.requester);
        }

        sourceStack.shrink(give);
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
