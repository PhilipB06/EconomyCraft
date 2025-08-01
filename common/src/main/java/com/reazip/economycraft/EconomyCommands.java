package com.reazip.economycraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.ClickEvent;

import java.util.Comparator;
import java.util.UUID;
import java.util.List;

import com.reazip.economycraft.shop.ShopManager;
import com.reazip.economycraft.shop.ShopListing;
import com.reazip.economycraft.shop.ShopUi;
import com.reazip.economycraft.market.MarketManager;
import com.reazip.economycraft.market.MarketRequest;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class EconomyCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("eco")
            .then(literal("balance")
                .executes(ctx -> showBalance(ctx.getSource().getPlayerOrException(), ctx.getSource()))
                .then(argument("player", EntityArgument.player())
                    .executes(ctx -> showBalance(EntityArgument.getPlayer(ctx, "player"), ctx.getSource()))))
            .then(literal("pay")
                .then(argument("player", EntityArgument.player())
                    .then(argument("amount", LongArgumentType.longArg(1))
                        .executes(ctx -> pay(ctx.getSource().getPlayerOrException(),
                                EntityArgument.getPlayer(ctx, "player"),
                                LongArgumentType.getLong(ctx, "amount"), ctx.getSource())))))
            .then(literal("addmoney").requires(s -> s.hasPermission(2))
                .then(argument("player", EntityArgument.player())
                    .then(argument("amount", LongArgumentType.longArg(1))
                        .executes(ctx -> addMoney(EntityArgument.getPlayer(ctx, "player"),
                                LongArgumentType.getLong(ctx, "amount"), ctx.getSource())))))
            .then(literal("setmoney").requires(s -> s.hasPermission(2))
                .then(argument("player", EntityArgument.player())
                    .then(argument("amount", LongArgumentType.longArg(0))
                        .executes(ctx -> setMoney(EntityArgument.getPlayer(ctx, "player"),
                                LongArgumentType.getLong(ctx, "amount"), ctx.getSource())))))
            .then(literal("shop")
                .executes(ctx -> openShop(ctx.getSource().getPlayerOrException(), ctx.getSource()))
                .then(literal("sell")
                    .then(argument("price", LongArgumentType.longArg(1))
                        .executes(ctx -> sellItem(ctx.getSource().getPlayerOrException(), LongArgumentType.getLong(ctx, "price"), ctx.getSource())))))
            .then(literal("market")
                .then(literal("list").executes(ctx -> listMarket(ctx.getSource())))
                .then(literal("request")
                    .then(argument("price", LongArgumentType.longArg(1))
                        .executes(ctx -> requestItem(ctx.getSource().getPlayerOrException(), LongArgumentType.getLong(ctx, "price"), ctx.getSource()))))
                .then(literal("fulfill")
                    .then(argument("id", LongArgumentType.longArg(1))
                        .executes(ctx -> fulfillRequest(ctx.getSource().getPlayerOrException(), LongArgumentType.getLong(ctx, "id"), ctx.getSource()))))
                .then(literal("claim").executes(ctx -> claimMarket(ctx.getSource().getPlayerOrException(), ctx.getSource()))))
        );
    }

    private static int showBalance(ServerPlayer player, CommandSourceStack source) {
        long bal = EconomyCraft.getManager(source.getServer()).getBalance(player.getUUID());
        source.sendSuccess(() -> Component.literal(player.getName().getString() + " balance: " + EconomyCraft.formatMoney(bal)), false);
        return 1;
    }

    private static int pay(ServerPlayer from, ServerPlayer to, long amount, CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        if (manager.pay(from.getUUID(), to.getUUID(), amount)) {
            source.sendSuccess(() -> Component.literal("Paid " + EconomyCraft.formatMoney(amount) + " to " + to.getName().getString()), false);
        } else {
            source.sendFailure(Component.literal("Not enough balance"));
        }
        return 1;
    }

    private static int addMoney(ServerPlayer player, long amount, CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        manager.addMoney(player.getUUID(), amount);
        source.sendSuccess(() -> Component.literal("Added " + EconomyCraft.formatMoney(amount) + " to " + player.getName().getString()), false);
        return 1;
    }

    private static int setMoney(ServerPlayer player, long amount, CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        manager.setMoney(player.getUUID(), amount);
        source.sendSuccess(() -> Component.literal("Set balance of " + player.getName().getString() + " to " + EconomyCraft.formatMoney(amount)), false);
        return 1;
    }

    // --- shop commands ---
    private static int openShop(ServerPlayer player, CommandSourceStack source) {
        ShopUi.open(player, EconomyCraft.getManager(source.getServer()).getShop());
        return 1;
    }

    private static int sellItem(ServerPlayer player, long price, CommandSourceStack source) {
        if (player.getMainHandItem().isEmpty()) {
            source.sendFailure(Component.literal("Hold the item to sell in your hand"));
            return 0;
        }
        ShopManager shop = EconomyCraft.getManager(source.getServer()).getShop();
        ShopListing listing = new ShopListing();
        listing.seller = player.getUUID();
        listing.price = price;
        listing.item = player.getMainHandItem().copy();
        player.getMainHandItem().setCount(0);
        shop.addListing(listing);
        source.sendSuccess(() -> Component.literal("Listed item for " + EconomyCraft.formatMoney(price)), false);
        return 1;
    }

    // --- market commands ---
    private static int listMarket(CommandSourceStack source) {
        MarketManager market = EconomyCraft.getManager(source.getServer()).getMarket();
        if (market.getRequests().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No requests"), false);
        } else {
            for (MarketRequest r : market.getRequests()) {
                String buyer = source.getServer().getProfileCache().get(r.requester).map(p -> p.getName()).orElse(r.requester.toString());
                Component msg = Component.literal("[" + r.id + "] " + buyer + " wants " + r.item.getCount() + "x " + r.item.getHoverName().getString() + " for " + EconomyCraft.formatMoney(r.price))
                        .append(Component.literal(" [Fulfill]").withStyle(style -> style.withUnderlined(true).withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/eco market fulfill " + r.id))));
                source.sendSuccess(() -> msg, false);
            }
        }
        return 1;
    }

    private static int requestItem(ServerPlayer player, long price, CommandSourceStack source) {
        if (player.getMainHandItem().isEmpty()) {
            source.sendFailure(Component.literal("Hold the requested item in your hand"));
            return 0;
        }
        MarketManager market = EconomyCraft.getManager(source.getServer()).getMarket();
        MarketRequest r = new MarketRequest();
        r.requester = player.getUUID();
        r.price = price;
        r.item = player.getMainHandItem().copy();
        r.item.setCount(player.getMainHandItem().getCount());
        player.getMainHandItem().setCount(0);
        market.addRequest(r);
        source.sendSuccess(() -> Component.literal("Created request"), false);
        return 1;
    }

    private static int fulfillRequest(ServerPlayer player, long id, CommandSourceStack source) {
        MarketManager market = EconomyCraft.getManager(source.getServer()).getMarket();
        MarketRequest req = null;
        for (MarketRequest r : market.getRequests()) {
            if (r.id == id) { req = r; break; }
        }
        if (req == null) {
            source.sendFailure(Component.literal("Request not found"));
            return 0;
        }
        if (!player.getMainHandItem().is(req.item.getItem()) || player.getMainHandItem().getCount() < req.item.getCount()) {
            source.sendFailure(Component.literal("Hold the required items"));
            return 0;
        }
        player.getMainHandItem().shrink(req.item.getCount());
        EconomyManager eco = EconomyCraft.getManager(source.getServer());
        eco.pay(req.requester, player.getUUID(), req.price);
        market.removeRequest(req.id);
        market.addDelivery(req.requester, req.item.copy());
        source.sendSuccess(() -> Component.literal("Fulfilled request"), false);
        return 1;
    }

    private static int claimMarket(ServerPlayer player, CommandSourceStack source) {
        MarketManager market = EconomyCraft.getManager(source.getServer()).getMarket();
        List<ItemStack> list = market.claimDeliveries(player.getUUID());
        if (list == null || list.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No items to claim"), false);
        } else {
            for (ItemStack s : list) {
                if (!player.getInventory().add(s)) {
                    player.drop(s, false);
                }
            }
            source.sendSuccess(() -> Component.literal("Claimed items"), false);
        }
        return 1;
    }
}
