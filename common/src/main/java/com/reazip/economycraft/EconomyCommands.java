package com.reazip.economycraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

import com.reazip.economycraft.shop.ShopManager;
import com.reazip.economycraft.shop.ShopListing;
import com.reazip.economycraft.shop.ShopUi;
import com.reazip.economycraft.market.MarketManager;
import com.reazip.economycraft.market.MarketRequest;
import com.reazip.economycraft.market.MarketUi;
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
                .then(argument("target", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        var server = ctx.getSource().getServer();
                        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                            builder.suggest(p.getGameProfile().getName());
                        }
                        builder.suggest("@a");
                        return builder.buildFuture();
                    })
                    .then(argument("amount", LongArgumentType.longArg(0))
                        .executes(ctx -> setMoney(StringArgumentType.getString(ctx, "target"),
                                LongArgumentType.getLong(ctx, "amount"), ctx.getSource())))))
            .then(literal("shop")
                .executes(ctx -> openShop(ctx.getSource().getPlayerOrException(), ctx.getSource()))
                .then(literal("sell")
                    .then(argument("price", LongArgumentType.longArg(1))
                        .executes(ctx -> sellItem(ctx.getSource().getPlayerOrException(), LongArgumentType.getLong(ctx, "price"), ctx.getSource())))))
            .then(literal("market")
                .executes(ctx -> openMarket(ctx.getSource().getPlayerOrException(), ctx.getSource()))
                .then(literal("request")
                    .then(argument("item", ResourceLocationArgument.id())
                        .then(argument("amount", LongArgumentType.longArg(1))
                            .then(argument("price", LongArgumentType.longArg(1))
                                .executes(ctx -> requestItem(ctx.getSource().getPlayerOrException(),
                                        ResourceLocationArgument.getId(ctx, "item"),
                                        (int)LongArgumentType.getLong(ctx, "amount"),
                                        LongArgumentType.getLong(ctx, "price"),
                                        ctx.getSource()))))))
                .then(literal("claim").executes(ctx -> claimMarket(ctx.getSource().getPlayerOrException(), ctx.getSource()))))
            .then(literal("daily")
                .executes(ctx -> daily(ctx.getSource().getPlayerOrException(), ctx.getSource())))
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
            to.sendSystemMessage(Component.literal(from.getName().getString() + " sent you " + EconomyCraft.formatMoney(amount)));
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

    private static int setMoney(String target, long amount, CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        if ("@a".equals(target)) {
            for (UUID id : new java.util.ArrayList<>(manager.getBalances().keySet())) {
                manager.setMoney(id, amount);
            }
            for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
                manager.setMoney(p.getUUID(), amount);
            }
            source.sendSuccess(() -> Component.literal("Set balance of all players to " + EconomyCraft.formatMoney(amount)), true);
            return 1;
        }
        var profile = source.getServer().getProfileCache().get(target);
        if (profile.isEmpty()) {
            source.sendFailure(Component.literal("Unknown player"));
            return 0;
        }
        manager.setMoney(profile.get().getId(), amount);
        source.sendSuccess(() -> Component.literal("Set balance of " + target + " to " + EconomyCraft.formatMoney(amount)), false);
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
        ItemStack hand = player.getMainHandItem();
        int count = Math.min(hand.getCount(), hand.getMaxStackSize());
        listing.item = hand.copyWithCount(count);
        hand.shrink(count);
        shop.addListing(listing);
        source.sendSuccess(() -> Component.literal("Listed item for " + EconomyCraft.formatMoney(price)), false);
        return 1;
    }

    // --- market commands ---
    private static int openMarket(ServerPlayer player, CommandSourceStack source) {
        MarketUi.openRequests(player, EconomyCraft.getManager(source.getServer()));
        return 1;
    }

    private static int requestItem(ServerPlayer player, ResourceLocation item, int amount, long price, CommandSourceStack source) {
        var holder = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(item);
        if (holder.isEmpty()) {
            source.sendFailure(Component.literal("Invalid item"));
            return 0;
        }
        ItemStack proto = new ItemStack(holder.get());
        amount = Math.min(amount, proto.getMaxStackSize());
        MarketManager market = EconomyCraft.getManager(source.getServer()).getMarket();
        MarketRequest r = new MarketRequest();
        r.requester = player.getUUID();
        r.price = price;
        r.item = new ItemStack(holder.get(), amount);
        market.addRequest(r);
        source.sendSuccess(() -> Component.literal("Created request"), false);
        return 1;
    }

    private static int claimMarket(ServerPlayer player, CommandSourceStack source) {
        MarketUi.openClaims(player, EconomyCraft.getManager(source.getServer()).getMarket());
        return 1;
    }

    private static int daily(ServerPlayer player, CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        if (manager.claimDaily(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("Claimed " + EconomyCraft.formatMoney(EconomyConfig.get().dailyAmount)), false);
        } else {
            source.sendFailure(Component.literal("Already claimed today"));
        }
        return 1;
    }
}
