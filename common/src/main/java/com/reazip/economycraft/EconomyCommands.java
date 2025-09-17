package com.reazip.economycraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;

import java.util.UUID;
import java.util.Collection;

import com.reazip.economycraft.shop.ShopManager;
import com.reazip.economycraft.shop.ShopListing;
import com.reazip.economycraft.shop.ShopUi;
import com.reazip.economycraft.orders.OrderManager;
import com.reazip.economycraft.orders.OrderRequest;
import com.reazip.economycraft.orders.OrdersUi;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import net.minecraft.commands.arguments.GameProfileArgument;
import com.mojang.authlib.GameProfile;

public final class EconomyCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildRoot());

        if (EconomyConfig.get().standaloneCommands) {
            dispatcher.register(buildBalance());
            dispatcher.register(buildPay());
            dispatcher.register(buildShop());
            dispatcher.register(buildOrders());
            dispatcher.register(buildDaily());
        }
        if (EconomyConfig.get().standaloneAdminCommands) {
            dispatcher.register(buildAddMoney());
            dispatcher.register(buildSetMoney());
            dispatcher.register(buildRemoveMoney());
            dispatcher.register(buildRemovePlayer());
            dispatcher.register(buildToggleScoreboard());
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot() {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("eco");
        root.then(buildBalance());
        root.then(buildPay());
        root.then(buildAddMoney());
        root.then(buildSetMoney());
        root.then(buildRemoveMoney());
        root.then(buildRemovePlayer());
        root.then(buildShop());
        root.then(buildOrders());
        root.then(buildDaily());
        root.then(buildToggleScoreboard());
        return root;
    }

    // --- balance & payments ---
    private static LiteralArgumentBuilder<CommandSourceStack> buildBalance() {
        return literal("balance")
                .executes(ctx -> showBalance(ctx.getSource().getPlayerOrException().getGameProfile(), ctx.getSource()))
                .then(argument("target", GameProfileArgument.gameProfile())
                        .executes(ctx -> {
                            Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "target");
                            if (profiles.size() != 1) {
                                ctx.getSource().sendFailure(Component.literal("Please specify exactly one player").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            return showBalance(profiles.iterator().next(), ctx.getSource());
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildPay() {
        return literal("pay")
                .then(argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestPlayers(ctx.getSource(), builder))
                        .then(argument("amount", LongArgumentType.longArg(1, EconomyManager.MAX))
                                .executes(ctx -> pay(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "player"),
                                        LongArgumentType.getLong(ctx, "amount"), ctx.getSource()))));
    }

    private static int showBalance(GameProfile profile, CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        Long bal = manager.getBalance(profile.getId(), false);

        if (bal == null) {
            source.sendFailure(Component.literal("Unknown player").withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerPlayer executor;
        try {
            executor = source.getPlayerOrException();
        } catch (Exception e) {
            executor = null;
        }

        if (executor != null && executor.getUUID().equals(profile.getId())) {
            source.sendSuccess(() -> Component.literal("Balance: " + EconomyCraft.formatMoney(bal))
                    .withStyle(ChatFormatting.YELLOW), false);
        } else {
            source.sendSuccess(() -> Component.literal(profile.getName() + "'s balance: " + EconomyCraft.formatMoney(bal))
                    .withStyle(ChatFormatting.YELLOW), false);
        }

        return 1;
    }


    private static int pay(ServerPlayer from, String target, long amount, CommandSourceStack source) {
        var profile = source.getServer().getProfileCache().get(target);
        if (profile.isEmpty()) {
            source.sendFailure(Component.literal("Unknown player").withStyle(ChatFormatting.RED));
            return 0;
        }
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        UUID toId = profile.get().getId();
        if (from.getUUID().equals(toId)) {
            source.sendFailure(Component.literal("You cannot pay yourself").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!manager.getBalances().containsKey(toId)) {
            source.sendFailure(Component.literal("Unknown player").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (manager.pay(from.getUUID(), toId, amount)) {
            source.sendSuccess(() -> Component.literal("Paid " + EconomyCraft.formatMoney(amount) + " to " + profile.get().getName()).withStyle(ChatFormatting.GREEN), false);
            ServerPlayer to = source.getServer().getPlayerList().getPlayer(toId);
            if (to != null) {
                to.sendSystemMessage(Component.literal(from.getName().getString() + " sent you " + EconomyCraft.formatMoney(amount)).withStyle(ChatFormatting.GREEN));
            }
        } else {
            source.sendFailure(Component.literal("Not enough balance").withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    // --- admin economy commands ---
    private static LiteralArgumentBuilder<CommandSourceStack> buildAddMoney() {
        return literal("addmoney").requires(s -> s.hasPermission(2))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .then(argument("amount", LongArgumentType.longArg(1, EconomyManager.MAX))
                                .executes(ctx -> addMoney(
                                        GameProfileArgument.getGameProfiles(ctx, "targets"),
                                        LongArgumentType.getLong(ctx, "amount"),
                                        ctx.getSource()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSetMoney() {
        return literal("setmoney").requires(s -> s.hasPermission(2))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .then(argument("amount", LongArgumentType.longArg(0, EconomyManager.MAX))
                                .executes(ctx -> setMoney(
                                        GameProfileArgument.getGameProfiles(ctx, "targets"),
                                        LongArgumentType.getLong(ctx, "amount"),
                                        ctx.getSource()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRemoveMoney() {
        return literal("removemoney").requires(s -> s.hasPermission(2))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .executes(ctx -> removeMoney(
                                GameProfileArgument.getGameProfiles(ctx, "targets"),
                                null,
                                ctx.getSource()))
                        .then(argument("amount", LongArgumentType.longArg(1, EconomyManager.MAX))
                                .executes(ctx -> removeMoney(
                                        GameProfileArgument.getGameProfiles(ctx, "targets"),
                                        LongArgumentType.getLong(ctx, "amount"),
                                        ctx.getSource()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRemovePlayer() {
        return literal("removeplayer").requires(s -> s.hasPermission(2))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .executes(ctx -> removePlayers(
                                GameProfileArgument.getGameProfiles(ctx, "targets"),
                                ctx.getSource())));
    }

    private static int addMoney(Collection<GameProfile> profiles, long amount, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }
        EconomyManager manager = EconomyCraft.getManager(source.getServer());

        if (profiles.size() == 1) {
            GameProfile p = profiles.iterator().next();
            manager.addMoney(p.getId(), amount);
            source.sendSuccess(() -> Component.literal(
                            "Added " + EconomyCraft.formatMoney(amount) + " to " + p.getName() + "'s balance.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        for (GameProfile p : profiles) {
            manager.addMoney(p.getId(), amount);
        }
        int count = profiles.size();
        source.sendSuccess(() -> Component.literal(
                        "Added " + EconomyCraft.formatMoney(amount) + " to " + count + " player" + (count > 1 ? "s" : ""))
                .withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static int setMoney(Collection<GameProfile> profiles, long amount, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }
        EconomyManager manager = EconomyCraft.getManager(source.getServer());

        if (profiles.size() == 1) {
            GameProfile p = profiles.iterator().next();
            manager.setMoney(p.getId(), amount);
            source.sendSuccess(() -> Component.literal(
                            "Set balance of " + p.getName() + " to " + EconomyCraft.formatMoney(amount))
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        for (GameProfile p : profiles) {
            manager.setMoney(p.getId(), amount);
        }
        int count = profiles.size();
        source.sendSuccess(() -> Component.literal(
                        "Set balance to " + EconomyCraft.formatMoney(amount) + " for " + count + " player" + (count > 1 ? "s" : ""))
                .withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static int removeMoney(Collection<GameProfile> profiles, Long amount, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        int success = 0;

        if (profiles.size() == 1) {
            GameProfile p = profiles.iterator().next();
            UUID id = p.getId();

            if (amount == null) {
                if (!manager.getBalances().containsKey(id)) {
                    source.sendFailure(Component.literal(
                            "Failed to remove all money from " + p.getName() + "'s balance. Unknown player.")
                            .withStyle(ChatFormatting.RED));
                    return 1;
                }
                manager.setMoney(id, 0L);
                source.sendSuccess(() -> Component.literal(
                        "Removed all money from " + p.getName() + "'s balance.")
                        .withStyle(ChatFormatting.GREEN), true);
                return 1;
            }

            if (!manager.removeMoney(id, amount)) {
                source.sendFailure(Component.literal(
                        "Failed to remove " + EconomyCraft.formatMoney(amount) + " from " + p.getName() + "'s balance due to insufficient funds.")
                        .withStyle(ChatFormatting.RED));
                return 1;
            }
            source.sendSuccess(() -> Component.literal(
                    "Successfully removed " + EconomyCraft.formatMoney(amount) + " from " + p.getName() + "'s balance.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        // Multi-targets
        for (GameProfile p : profiles) {
            UUID id = p.getId();
            if (amount == null) {
                if (!manager.getBalances().containsKey(id)) {
                    source.sendFailure(Component.literal(
                            "Failed to remove all money from " + p.getName() + "'s balance. Unknown player.")
                            .withStyle(ChatFormatting.RED));
                    continue;
                }
                manager.setMoney(id, 0L);
                success++;
            } else {
                if (manager.removeMoney(id, amount)) {
                    success++;
                } else {
                    source.sendFailure(Component.literal(
                            "Failed to remove " + EconomyCraft.formatMoney(amount) + " from " + p.getName() + "'s balance due to insufficient funds.")
                            .withStyle(ChatFormatting.RED));
                }
            }
        }

        if (success > 0) {
            int finalSuccess = success;
            if (amount == null) {
                source.sendSuccess(() -> Component.literal(
                        "Removed all money from " + finalSuccess + " player" + (finalSuccess > 1 ? "s" : "") + ".")
                        .withStyle(ChatFormatting.GREEN), true);
            } else {
                source.sendSuccess(() -> Component.literal(
                        "Successfully removed " + EconomyCraft.formatMoney(amount) + " from " + finalSuccess + " player" + (finalSuccess > 1 ? "s" : "") + ".")
                        .withStyle(ChatFormatting.GREEN), true);
            }
        }

        return profiles.size();
    }

    private static int removePlayers(Collection<GameProfile> profiles, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }
        EconomyManager manager = EconomyCraft.getManager(source.getServer());

        if (profiles.size() == 1) {
            GameProfile p = profiles.iterator().next();
            manager.removePlayer(p.getId());
            source.sendSuccess(() -> Component.literal("Removed " + p.getName() + " from economy")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        for (GameProfile p : profiles) {
            manager.removePlayer(p.getId());
        }
        int count = profiles.size();
        source.sendSuccess(() -> Component.literal(
                "Removed " + count + " player" + (count > 1 ? "s" : "") + " from economy")
                .withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    // --- scoreboard toggle ---
    private static LiteralArgumentBuilder<CommandSourceStack> buildToggleScoreboard() {
        return literal("toggleScoreboard").requires(s -> s.hasPermission(2))
                .executes(ctx -> toggleScoreboard(ctx.getSource()));
    }

    private static int toggleScoreboard(CommandSourceStack source) {
        boolean enabled = EconomyCraft.getManager(source.getServer()).toggleScoreboard();
        source.sendSuccess(() -> Component.literal("Scoreboard " + (enabled ? "enabled" : "disabled"))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return 1;
    }

    // --- shop commands ---
    private static LiteralArgumentBuilder<CommandSourceStack> buildShop() {
        return literal("shop")
                .executes(ctx -> openShop(ctx.getSource().getPlayerOrException(), ctx.getSource()))
                .then(literal("sell")
                        .then(argument("price", LongArgumentType.longArg(1, EconomyManager.MAX))
                                .executes(ctx -> sellItem(ctx.getSource().getPlayerOrException(), LongArgumentType.getLong(ctx, "price"), ctx.getSource()))));
    }

    private static int openShop(ServerPlayer player, CommandSourceStack source) {
        ShopUi.open(player, EconomyCraft.getManager(source.getServer()).getShop());
        return 1;
    }

    private static int sellItem(ServerPlayer player, long price, CommandSourceStack source) {
        if (player.getMainHandItem().isEmpty()) {
            source.sendFailure(Component.literal("Hold the item to sell in your hand").withStyle(ChatFormatting.RED));
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
        long tax = Math.round(price * EconomyConfig.get().taxRate);
        source.sendSuccess(() -> Component.literal("Listed item for " + EconomyCraft.formatMoney(price) + " (buyers pay " + EconomyCraft.formatMoney(price + tax) + ")").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // --- orders commands ---
    private static LiteralArgumentBuilder<CommandSourceStack> buildOrders() {
        return literal("orders")
                .executes(ctx -> openOrders(ctx.getSource().getPlayerOrException(), ctx.getSource()))
                .then(literal("request")
                        .then(argument("item", ResourceLocationArgument.id())
                                .then(argument("amount", LongArgumentType.longArg(1, EconomyManager.MAX))
                                        .then(argument("price", LongArgumentType.longArg(1, EconomyManager.MAX))
                                                .executes(ctx -> requestItem(ctx.getSource().getPlayerOrException(),
                                                        ResourceLocationArgument.getId(ctx, "item"),
                                                        (int) Math.min(LongArgumentType.getLong(ctx, "amount"), EconomyManager.MAX),
                                                        LongArgumentType.getLong(ctx, "price"),
                                                        ctx.getSource()))))))
                .then(literal("claim").executes(ctx -> claimOrders(ctx.getSource().getPlayerOrException(), ctx.getSource())));
    }

    private static int openOrders(ServerPlayer player, CommandSourceStack source) {
        OrdersUi.open(player, EconomyCraft.getManager(source.getServer()));
        return 1;
    }

    private static int requestItem(ServerPlayer player, ResourceLocation item, int amount, long price, CommandSourceStack source) {
        var holder = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(item);
        if (holder.isEmpty()) {
            source.sendFailure(Component.literal("Invalid item").withStyle(ChatFormatting.RED));
            return 0;
        }
        OrderManager orders = EconomyCraft.getManager(source.getServer()).getOrders();
        OrderRequest r = new OrderRequest();
        r.requester = player.getUUID();
        r.price = price;
        r.item = new ItemStack(holder.get());
        int maxAmount = 36 * r.item.getMaxStackSize();
        if (amount > maxAmount) {
            source.sendFailure(Component.literal("Amount exceeds 36 stacks (max " + maxAmount + ")").withStyle(ChatFormatting.RED));
            return 0;
        }
        r.amount = amount;
        orders.addRequest(r);
        long tax = Math.round(price * EconomyConfig.get().taxRate);
        source.sendSuccess(() -> Component.literal("Created request (fulfiller receives " + EconomyCraft.formatMoney(price - tax) + ")").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int claimOrders(ServerPlayer player, CommandSourceStack source) {
        OrdersUi.openClaims(player, EconomyCraft.getManager(source.getServer()));
        return 1;
    }

    // --- daily reward ---
    private static LiteralArgumentBuilder<CommandSourceStack> buildDaily() {
        return literal("daily")
                .executes(ctx -> daily(ctx.getSource().getPlayerOrException(), ctx.getSource()));
    }

    private static int daily(ServerPlayer player, CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        if (manager.claimDaily(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("Claimed " + EconomyCraft.formatMoney(EconomyConfig.get().dailyAmount)).withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendFailure(Component.literal("Already claimed today").withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    // --- helpers ---
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(
            CommandSourceStack source, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        var server = source.getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            builder.suggest(p.getGameProfile().getName());
        }
        for (UUID id : EconomyCraft.getManager(server).getBalances().keySet()) {
            var prof = server.getProfileCache().get(id);
            prof.ifPresent(gameProfile -> builder.suggest(gameProfile.getName()));
        }
        return builder.buildFuture();
    }
}
