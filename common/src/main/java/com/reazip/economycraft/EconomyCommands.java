package com.reazip.economycraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.reazip.economycraft.util.IdentityCompat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;

import java.util.Set;
import java.util.UUID;
import java.util.Collection;

import java.util.concurrent.CompletableFuture;
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

    // =====================================================================
    // === Balance & payments ==============================================
    // =====================================================================

    private static LiteralArgumentBuilder<CommandSourceStack> buildBalance() {
        return literal("balance")
                .executes(ctx -> showBalance(IdentityCompat.of(ctx.getSource().getPlayerOrException()), ctx.getSource()))
                .then(argument("target", GameProfileArgument.gameProfile())
                        .executes(ctx -> {
                            var refs = IdentityCompat.getArgAsPlayerRefs(ctx, "target");
                            if (refs.size() != 1) {
                                ctx.getSource().sendFailure(Component.literal("Please specify exactly one player").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            return showBalance(refs.iterator().next(), ctx.getSource());
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

    private static int showBalance(IdentityCompat.PlayerRef target, CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        Long bal = manager.getBalance(target.id(), false);
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

        if (executor != null && executor.getUUID().equals(target.id())) {
            source.sendSuccess(() -> Component.literal("Balance: " + EconomyCraft.formatMoney(bal))
                    .withStyle(ChatFormatting.YELLOW), false);
        } else {
            source.sendSuccess(() -> Component.literal(target.name() + "'s balance: " + EconomyCraft.formatMoney(bal))
                    .withStyle(ChatFormatting.YELLOW), false);
        }

        return 1;
    }

    private static int pay(ServerPlayer from, String target, long amount, CommandSourceStack source) {
        var server = source.getServer();
        EconomyManager manager = EconomyCraft.getManager(server);

        ServerPlayer toOnline = server.getPlayerList().getPlayerByName(target);
        UUID toId = (toOnline != null) ? toOnline.getUUID() : null;

        if (toId == null) {
            try { toId = java.util.UUID.fromString(target); } catch (IllegalArgumentException ignored) {}
        }

        if (toId == null) {
            toId = manager.tryResolveUuidByName(target);
        }

        if (toId == null) {
            source.sendFailure(Component.literal("Unknown player").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (from.getUUID().equals(toId)) {
            source.sendFailure(Component.literal("You cannot pay yourself").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!manager.getBalances().containsKey(toId)) {
            source.sendFailure(Component.literal("Unknown player").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (manager.pay(from.getUUID(), toId, amount)) {
            String displayName = (toOnline != null)
                    ? IdentityCompat.of(toOnline).name()
                    : getDisplayName(manager, toId);

            source.sendSuccess(() -> Component.literal("Paid " + EconomyCraft.formatMoney(amount) + " to " + displayName)
                    .withStyle(ChatFormatting.GREEN), false);

            if (toOnline != null) {
                toOnline.sendSystemMessage(Component.literal(from.getName().getString() + " sent you " + EconomyCraft.formatMoney(amount))
                        .withStyle(ChatFormatting.GREEN));
            }
        } else {
            source.sendFailure(Component.literal("Not enough balance").withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    // =====================================================================
    // === Admin commands ==================================================
    // =====================================================================

    private static LiteralArgumentBuilder<CommandSourceStack> buildAddMoney() {
        return literal("addmoney").requires(s -> s.hasPermission(2))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .then(argument("amount", LongArgumentType.longArg(1, EconomyManager.MAX))
                                .executes(ctx -> addMoney(
                                        IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                        LongArgumentType.getLong(ctx, "amount"),
                                        ctx.getSource()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSetMoney() {
        return literal("setmoney").requires(s -> s.hasPermission(2))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .then(argument("amount", LongArgumentType.longArg(0, EconomyManager.MAX))
                                .executes(ctx -> setMoney(
                                        IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                        LongArgumentType.getLong(ctx, "amount"),
                                        ctx.getSource()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRemoveMoney() {
        return literal("removemoney").requires(s -> s.hasPermission(2))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .executes(ctx -> removeMoney(
                                IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                null,
                                ctx.getSource()))
                        .then(argument("amount", LongArgumentType.longArg(1, EconomyManager.MAX))
                                .executes(ctx -> removeMoney(
                                        IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                        LongArgumentType.getLong(ctx, "amount"),
                                        ctx.getSource()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRemovePlayer() {
        return literal("removeplayer").requires(s -> s.hasPermission(2))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .executes(ctx -> removePlayers(
                                IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                ctx.getSource())));
    }

    private static int addMoney(Collection<IdentityCompat.PlayerRef> profiles, long amount, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            manager.addMoney(p.id(), amount);
            source.sendSuccess(() -> Component.literal(
                            "Added " + EconomyCraft.formatMoney(amount) + " to " + p.name() + "'s balance.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        for (var p : profiles) {
            manager.addMoney(p.id(), amount);
        }

        int count = profiles.size();
        source.sendSuccess(() -> Component.literal(
                        "Added " + EconomyCraft.formatMoney(amount) + " to " + count + " player" + (count > 1 ? "s" : ""))
                .withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static int setMoney(Collection<IdentityCompat.PlayerRef> profiles, long amount, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            manager.setMoney(p.id(), amount);
            source.sendSuccess(() -> Component.literal(
                            "Set balance of " + p.name() + " to " + EconomyCraft.formatMoney(amount))
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        for (var p : profiles) {
            manager.setMoney(p.id(), amount);
        }

        int count = profiles.size();
        source.sendSuccess(() -> Component.literal(
                        "Set balance to " + EconomyCraft.formatMoney(amount) + " for " + count + " player" + (count > 1 ? "s" : ""))
                .withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static int removeMoney(Collection<IdentityCompat.PlayerRef> profiles, Long amount, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        int success = 0;

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            UUID id = p.id();

            if (amount == null) {
                if (!manager.getBalances().containsKey(id)) {
                    source.sendFailure(Component.literal(
                                    "Failed to remove all money from " + p.name() + "'s balance. Unknown player.")
                            .withStyle(ChatFormatting.RED));
                    return 1;
                }
                manager.setMoney(id, 0L);
                source.sendSuccess(() -> Component.literal(
                                "Removed all money from " + p.name() + "'s balance.")
                        .withStyle(ChatFormatting.GREEN), true);
                return 1;
            }

            if (!manager.removeMoney(id, amount)) {
                source.sendFailure(Component.literal(
                                "Failed to remove " + EconomyCraft.formatMoney(amount) + " from " + p.name() + "'s balance due to insufficient funds.")
                        .withStyle(ChatFormatting.RED));
                return 1;
            }
            source.sendSuccess(() -> Component.literal(
                            "Successfully removed " + EconomyCraft.formatMoney(amount) + " from " + p.name() + "'s balance.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        // Multi-targets
        for (var p : profiles) {
            UUID id = p.id();
            if (amount == null) {
                if (!manager.getBalances().containsKey(id)) {
                    source.sendFailure(Component.literal(
                                    "Failed to remove all money from " + p.name() + "'s balance. Unknown player.")
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
                                    "Failed to remove " + EconomyCraft.formatMoney(amount) + " from " + p.name() + "'s balance due to insufficient funds.")
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

    private static int removePlayers(Collection<IdentityCompat.PlayerRef> profiles, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            manager.removePlayer(p.id());
            source.sendSuccess(() -> Component.literal("Removed " + p.name() + " from economy")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        for (var p : profiles) {
            manager.removePlayer(p.id());
        }

        int count = profiles.size();
        source.sendSuccess(() -> Component.literal(
                        "Removed " + count + " player" + (count > 1 ? "s" : "") + " from economy")
                .withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    // =====================================================================
    // === Scoreboard toggle ===============================================
    // =====================================================================

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

    // =====================================================================
    // === Shop commands ===================================================
    // =====================================================================

    private static LiteralArgumentBuilder<CommandSourceStack> buildShop() {
        return literal("shop")
                .executes(ctx -> openShop(ctx.getSource().getPlayerOrException(), ctx.getSource()))
                .then(literal("sell")
                        .then(argument("price", LongArgumentType.longArg(1, EconomyManager.MAX))
                                .executes(ctx -> sellItem(ctx.getSource().getPlayerOrException(),
                                        LongArgumentType.getLong(ctx, "price"),
                                        ctx.getSource()))));
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

    // =====================================================================
    // === Orders commands =================================================
    // =====================================================================

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

    // =====================================================================
    // === Daily reward ====================================================
    // =====================================================================

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

    // =====================================================================
    // === Helpers ========================================================
    // =====================================================================

    private static String getDisplayName(EconomyManager manager, UUID id) {
        var server = manager.getServer();
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return IdentityCompat.of(online).name();
        String name = manager.getBestName(id);
        if (name != null && !name.isBlank()) return name;
        return id.toString();
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandSourceStack source, SuggestionsBuilder builder) {
        var server = source.getServer();
        var manager = EconomyCraft.getManager(server);
        Set<String> suggestions = new java.util.HashSet<>();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            suggestions.add(IdentityCompat.of(p).name());
        }

        for (UUID id : manager.getBalances().keySet()) {
            String name = manager.getBestName(id);
            if (name != null && !name.isBlank()) {
                suggestions.add(name);
            }
        }

        suggestions.forEach(builder::suggest);
        return builder.buildFuture();
    }
}
