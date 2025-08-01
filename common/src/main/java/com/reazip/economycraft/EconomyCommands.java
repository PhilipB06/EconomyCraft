package com.reazip.economycraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

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
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Shop UI is not implemented yet."), false);
                    return 1;
                }))
            .then(literal("blackboard")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Blackboard feature is not implemented yet."), false);
                    return 1;
                }))
            .then(literal("top")
                .executes(ctx -> showTop(ctx.getSource()))));
    }

    private static int showBalance(ServerPlayer player, CommandSourceStack source) {
        long bal = EconomyCraft.getManager(source.getServer()).getBalance(player.getUUID());
        source.sendSuccess(() -> Component.literal(player.getName().getString() + " balance: " + bal), false);
        return 1;
    }

    private static int pay(ServerPlayer from, ServerPlayer to, long amount, CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        if (manager.pay(from.getUUID(), to.getUUID(), amount)) {
            source.sendSuccess(() -> Component.literal("Paid " + amount + " to " + to.getName().getString()), false);
        } else {
            source.sendFailure(Component.literal("Not enough balance"));
        }
        return 1;
    }

    private static int addMoney(ServerPlayer player, long amount, CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        manager.addMoney(player.getUUID(), amount);
        source.sendSuccess(() -> Component.literal("Added " + amount + " to " + player.getName().getString()), false);
        return 1;
    }

    private static int setMoney(ServerPlayer player, long amount, CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        manager.setMoney(player.getUUID(), amount);
        source.sendSuccess(() -> Component.literal("Set balance of " + player.getName().getString() + " to " + amount), false);
        return 1;
    }

    private static int showTop(CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        source.sendSuccess(() -> Component.literal("Top balances:"), false);
        manager.getBalances().entrySet().stream()
            .sorted(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder()))
            .limit(5)
            .forEach(e -> {
                String name = source.getServer().getProfileCache().get(e.getKey()).map(p -> p.getName()).orElse(e.getKey().toString());
                source.sendSuccess(() -> Component.literal(name + ": " + e.getValue()), false);
            });
        return 1;
    }
}
