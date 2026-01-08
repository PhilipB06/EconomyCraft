package com.reazip.economycraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.reazip.economycraft.util.IdentityCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

import java.util.concurrent.CompletableFuture;
import com.reazip.economycraft.shop.ShopManager;
import com.reazip.economycraft.shop.ShopListing;
import com.reazip.economycraft.shop.ShopUi;
import com.reazip.economycraft.shop.ServerShopUi;
import com.reazip.economycraft.orders.OrderManager;
import com.reazip.economycraft.orders.OrderRequest;
import com.reazip.economycraft.orders.OrdersUi;
import net.minecraft.world.item.ItemStack;
import com.reazip.economycraft.PriceRegistry;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EconomyCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildRoot());

        dispatcher.register(buildBalance().requires(s -> EconomyConfig.get().standaloneCommands));
        dispatcher.register(buildPay().requires(s -> EconomyConfig.get().standaloneCommands));
        dispatcher.register(SellCommand.register().requires(s -> EconomyConfig.get().standaloneCommands));
        dispatcher.register(buildShop().requires(s -> EconomyConfig.get().standaloneCommands));
        dispatcher.register(buildServerShop().requires(s -> EconomyConfig.get().standaloneCommands));
        dispatcher.register(buildOrders().requires(s -> EconomyConfig.get().standaloneCommands));
        dispatcher.register(buildDaily().requires(s -> EconomyConfig.get().standaloneCommands));

        var addMoney = buildAddMoney();
        addMoney.requires(addMoney.getRequirement().and(s -> EconomyConfig.get().standaloneAdminCommands));
        dispatcher.register(addMoney);

        var setMoney = buildSetMoney();
        setMoney.requires(setMoney.getRequirement().and(s -> EconomyConfig.get().standaloneAdminCommands));
        dispatcher.register(setMoney);

        var removeMoney = buildRemoveMoney();
        removeMoney.requires(removeMoney.getRequirement().and(s -> EconomyConfig.get().standaloneAdminCommands));
        dispatcher.register(removeMoney);

        var removePlayer = buildRemovePlayer();
        removePlayer.requires(removePlayer.getRequirement().and(s -> EconomyConfig.get().standaloneAdminCommands));
        dispatcher.register(removePlayer);

        var toggleScoreboard = buildToggleScoreboard();
        toggleScoreboard.requires(toggleScoreboard.getRequirement().and(s -> EconomyConfig.get().standaloneAdminCommands));
        dispatcher.register(toggleScoreboard);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot() {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("eco");
        root.then(buildBalance());
        root.then(buildPay());
        root.then(SellCommand.register());
        root.then(buildAddMoney());
        root.then(buildSetMoney());
        root.then(buildRemoveMoney());
        root.then(buildRemovePlayer());
        root.then(buildShop());
        root.then(buildServerShop());
        root.then(buildOrders());
        root.then(buildDaily());
        root.then(buildToggleScoreboard());
        return root;
    }

    // =====================================================================
    // === Balance & payments ==============================================
    // =====================================================================

    private static LiteralArgumentBuilder<CommandSourceStack> buildBalance() {
        return literal("bal")
                .then(literal("top")
                        .executes(ctx -> balTop(ctx.getSource())))
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

        Component msg;
        if (executor != null && executor.getUUID().equals(target.id())) {
            msg = Component.literal("Balance: " + EconomyCraft.formatMoney(bal))
                    .withStyle(ChatFormatting.YELLOW);
        } else {
            msg = Component.literal(target.name() + "'s balance: " + EconomyCraft.formatMoney(bal))
                    .withStyle(ChatFormatting.YELLOW);
        }

        if (executor != null) {
            executor.sendSystemMessage(msg);
        } else {
            source.sendSuccess(() -> msg, false);
        }

        return 1;
    }

    private static int balTop(CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        Map<UUID, Long> balances = manager.getBalances();

        if (balances.isEmpty()) {
            source.sendFailure(Component.literal("No balances found").withStyle(ChatFormatting.RED));
            return 0;
        }

        var sorted = getSortedEntries(balances, manager);
        if (sorted.size() > 10) sorted = new java.util.ArrayList<>(sorted.subList(0, 10));

        StringBuilder sb = new StringBuilder("Top balances:\n");
        for (int i = 0; i < sorted.size(); i++) {
            var e = sorted.get(i);
            UUID id = e.getKey();
            long balance = e.getValue();

            String name = manager.getBestName(id);
            if (name == null || name.isBlank()) name = id.toString();

            sb.append(i + 1)
                    .append(". ")
                    .append(name)
                    .append(": ")
                    .append(EconomyCraft.formatMoney(balance));

            if (i + 1 < sorted.size()) sb.append("\n");
        }

        Component msg = Component.literal(sb.toString()).withStyle(ChatFormatting.GOLD);

        ServerPlayer executor;
        try { executor = source.getPlayerOrException(); }
        catch (Exception ex) { executor = null; }

        if (executor != null) executor.sendSystemMessage(msg);
        else source.sendSuccess(() -> msg, false);

        return sorted.size();
    }

    private static @NotNull ArrayList<Map.Entry<UUID, Long>> getSortedEntries(Map<UUID, Long> balances, EconomyManager manager) {
        var sorted = new ArrayList<>(balances.entrySet());
        sorted.sort((a, b) -> {
            int c = Long.compare(b.getValue(), a.getValue());
            if (c != 0) return c;

            String an = manager.getBestName(a.getKey());
            String bn = manager.getBestName(b.getKey());
            if (an == null || an.isBlank()) an = a.getKey().toString();
            if (bn == null || bn.isBlank()) bn = b.getKey().toString();

            c = String.CASE_INSENSITIVE_ORDER.compare(an, bn);
            if (c != 0) return c;

            return a.getKey().compareTo(b.getKey());
        });
        return sorted;
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

            ServerPlayer executor;
            try {
                executor = source.getPlayerOrException();
            } catch (Exception e) {
                executor = null;
            }

            Component msg = Component.literal("Paid " + EconomyCraft.formatMoney(amount) + " to " + displayName)
                    .withStyle(ChatFormatting.GREEN);

            if (executor != null) {
                executor.sendSystemMessage(msg);
            } else {
                source.sendSuccess(() -> msg, false);
            }

            if (toOnline != null) {
                toOnline.sendSystemMessage(
                        Component.literal(from.getName().getString() + " sent you " + EconomyCraft.formatMoney(amount))
                                .withStyle(ChatFormatting.GREEN)
                );
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
        return literal("addmoney").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .then(argument("amount", LongArgumentType.longArg(1, EconomyManager.MAX))
                                .executes(ctx -> addMoney(
                                        IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                        LongArgumentType.getLong(ctx, "amount"),
                                        ctx.getSource()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSetMoney() {
        return literal("setmoney").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .then(argument("amount", LongArgumentType.longArg(0, EconomyManager.MAX))
                                .executes(ctx -> setMoney(
                                        IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                        LongArgumentType.getLong(ctx, "amount"),
                                        ctx.getSource()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRemoveMoney() {
        return literal("removemoney").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
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
        return literal("removeplayer").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
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

        ServerPlayer executor;
        try {
            executor = source.getPlayerOrException();
        } catch (Exception e) {
            executor = null;
        }

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            manager.addMoney(p.id(), amount);

            Component msg = Component.literal(
                            "Added " + EconomyCraft.formatMoney(amount) + " to " + p.name() + "'s balance.")
                    .withStyle(ChatFormatting.GREEN);

            if (executor != null) {
                executor.sendSystemMessage(msg);
            } else {
                source.sendSuccess(() -> msg, true);
            }

            return 1;
        }

        for (var p : profiles) {
            manager.addMoney(p.id(), amount);
        }

        int count = profiles.size();

        Component msg = Component.literal(
                        "Added " + EconomyCraft.formatMoney(amount) + " to " + count + " player" + (count > 1 ? "s" : ""))
                .withStyle(ChatFormatting.GREEN);

        if (executor != null) {
            executor.sendSystemMessage(msg);
        } else {
            source.sendSuccess(() -> msg, true);
        }

        return count;
    }

    private static int setMoney(Collection<IdentityCompat.PlayerRef> profiles, long amount, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());

        ServerPlayer executor;
        try {
            executor = source.getPlayerOrException();
        } catch (Exception e) {
            executor = null;
        }

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            manager.setMoney(p.id(), amount);

            Component msg = Component.literal(
                            "Set balance of " + p.name() + " to " + EconomyCraft.formatMoney(amount))
                    .withStyle(ChatFormatting.GREEN);

            if (executor != null) {
                executor.sendSystemMessage(msg);
            } else {
                source.sendSuccess(() -> msg, true);
            }

            return 1;
        }

        for (var p : profiles) {
            manager.setMoney(p.id(), amount);
        }

        int count = profiles.size();

        Component msg = Component.literal(
                        "Set balance to " + EconomyCraft.formatMoney(amount) + " for " + count + " player" + (count > 1 ? "s" : ""))
                .withStyle(ChatFormatting.GREEN);

        if (executor != null) {
            executor.sendSystemMessage(msg);
        } else {
            source.sendSuccess(() -> msg, true);
        }

        return count;
    }

    private static int removeMoney(Collection<IdentityCompat.PlayerRef> profiles, Long amount, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        ServerPlayer executor;
        try {
            executor = source.getPlayerOrException();
        } catch (Exception e) {
            executor = null;
        }

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
                Component msg = Component.literal(
                                "Removed all money from " + p.name() + "'s balance.")
                        .withStyle(ChatFormatting.GREEN);
                if (executor != null) {
                    executor.sendSystemMessage(msg);
                } else {
                    source.sendSuccess(() -> msg, true);
                }
                return 1;
            }

            if (!manager.removeMoney(id, amount)) {
                source.sendFailure(Component.literal(
                                "Failed to remove " + EconomyCraft.formatMoney(amount) + " from " + p.name() + "'s balance due to insufficient funds.")
                        .withStyle(ChatFormatting.RED));
                return 1;
            }

            Component msg = Component.literal(
                            "Successfully removed " + EconomyCraft.formatMoney(amount) + " from " + p.name() + "'s balance.")
                    .withStyle(ChatFormatting.GREEN);
            if (executor != null) {
                executor.sendSystemMessage(msg);
            } else {
                source.sendSuccess(() -> msg, true);
            }
            return 1;
        }

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
            Component msg;
            if (amount == null) {
                msg = Component.literal(
                                "Removed all money from " + finalSuccess + " player" + (finalSuccess > 1 ? "s" : "") + ".")
                        .withStyle(ChatFormatting.GREEN);
            } else {
                msg = Component.literal(
                                "Successfully removed " + EconomyCraft.formatMoney(amount) + " from " + finalSuccess + " player" + (finalSuccess > 1 ? "s" : "") + ".")
                        .withStyle(ChatFormatting.GREEN);
            }
            if (executor != null) {
                executor.sendSystemMessage(msg);
            } else {
                source.sendSuccess(() -> msg, true);
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
        ServerPlayer executor;
        try {
            executor = source.getPlayerOrException();
        } catch (Exception e) {
            executor = null;
        }

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            manager.removePlayer(p.id());

            Component msg = Component.literal("Removed " + p.name() + " from economy")
                    .withStyle(ChatFormatting.GREEN);

            if (executor != null) {
                executor.sendSystemMessage(msg);
            } else {
                source.sendSuccess(() -> msg, true);
            }

            return 1;
        }

        for (var p : profiles) {
            manager.removePlayer(p.id());
        }

        int count = profiles.size();

        Component msg = Component.literal(
                        "Removed " + count + " player" + (count > 1 ? "s" : "") + " from economy")
                .withStyle(ChatFormatting.GREEN);

        if (executor != null) {
            executor.sendSystemMessage(msg);
        } else {
            source.sendSuccess(() -> msg, true);
        }

        return count;
    }

    // =====================================================================
    // === Scoreboard toggle ===============================================
    // =====================================================================

    private static LiteralArgumentBuilder<CommandSourceStack> buildToggleScoreboard() {
        return literal("toggleScoreboard").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> toggleScoreboard(ctx.getSource()));
    }

    private static int toggleScoreboard(CommandSourceStack source) {
        boolean enabled = EconomyCraft.getManager(source.getServer()).toggleScoreboard();

        ServerPlayer executor;
        try {
            executor = source.getPlayerOrException();
        } catch (Exception e) {
            executor = null;
        }

        Component msg = Component.literal("Scoreboard " + (enabled ? "enabled" : "disabled"))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);

        if (executor != null) {
            executor.sendSystemMessage(msg);
        } else {
            source.sendSuccess(() -> msg, false);
        }

        return 1;
    }

    // =====================================================================
    // === Shop commands ===================================================
    // =====================================================================

    private static LiteralArgumentBuilder<CommandSourceStack> buildShop() {
        return literal("shop")
                .executes(ctx -> openShop(ctx.getSource().getPlayerOrException(), ctx.getSource()))
                .then(literal("list")
                        .then(argument("price", LongArgumentType.longArg(1, EconomyManager.MAX))
                                .executes(ctx -> listItem(ctx.getSource().getPlayerOrException(),
                                        LongArgumentType.getLong(ctx, "price"),
                                        ctx.getSource()))));
    }

    private static int openShop(ServerPlayer player, CommandSourceStack source) {
        ShopUi.open(player, EconomyCraft.getManager(source.getServer()).getShop());
        return 1;
    }

    private static int listItem(ServerPlayer player, long price, CommandSourceStack source) {
        if (player.getMainHandItem().isEmpty()) {
            source.sendFailure(Component.literal("Hold the item to list in your hand").withStyle(ChatFormatting.RED));
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

        Component msg = Component.literal("Listed item for " + EconomyCraft.formatMoney(price) +
                        (tax > 0 ? " (buyers pay " + EconomyCraft.formatMoney(price + tax) + ")" : ""))
                .withStyle(ChatFormatting.GREEN);

        player.sendSystemMessage(msg);

        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildServerShop() {
        return literal("servershop")
                .executes(ctx -> openServerShop(ctx.getSource().getPlayerOrException(), ctx.getSource(), null))
                .then(argument("category", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> suggestServerShopCategories(ctx.getSource(), builder))
                        .executes(ctx -> openServerShop(ctx.getSource().getPlayerOrException(),
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "category"))));
    }

    private static int openServerShop(ServerPlayer player, CommandSourceStack source, @Nullable String category) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        ServerShopUi.open(player, manager, category);
        return 1;
    }

    // =====================================================================
    // === Orders commands =================================================
    // =====================================================================

    private static LiteralArgumentBuilder<CommandSourceStack> buildOrders() {
        return literal("orders")
                .executes(ctx -> openOrders(ctx.getSource().getPlayerOrException(), ctx.getSource()))
                .then(literal("request")
                        .then(argument("item", IdentifierArgument.id())
                                .then(argument("amount", LongArgumentType.longArg(1, EconomyManager.MAX))
                                        .then(argument("price", LongArgumentType.longArg(1, EconomyManager.MAX))
                                                .executes(ctx -> requestItem(ctx.getSource().getPlayerOrException(),
                                                        IdentifierArgument.getId(ctx, "item"),
                                                        (int) Math.min(LongArgumentType.getLong(ctx, "amount"), EconomyManager.MAX),
                                                        LongArgumentType.getLong(ctx, "price"),
                                                        ctx.getSource()))))))
                .then(literal("claim").executes(ctx -> claimOrders(ctx.getSource().getPlayerOrException(), ctx.getSource())));
    }

    private static int openOrders(ServerPlayer player, CommandSourceStack source) {
        OrdersUi.open(player, EconomyCraft.getManager(source.getServer()));
        return 1;
    }

    private static int requestItem(ServerPlayer player, Identifier item, int amount, long price, CommandSourceStack source) {
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

        Component msg = Component.literal("Created request" +
                (tax > 0 ? " (fulfiller receives " + EconomyCraft.formatMoney(price - tax) + ")" : ""))
                .withStyle(ChatFormatting.GREEN);
        player.sendSystemMessage(msg);

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
            Component msg = Component.literal("Claimed " + EconomyCraft.formatMoney(EconomyConfig.get().dailyAmount))
                    .withStyle(ChatFormatting.GREEN);
            player.sendSystemMessage(msg);
        } else {
            source.sendFailure(Component.literal("Already claimed today").withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    // =====================================================================
    // === Helpers =========================================================
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

    private static CompletableFuture<Suggestions> suggestServerShopCategories(CommandSourceStack source, SuggestionsBuilder builder) {
        PriceRegistry prices = EconomyCraft.getManager(source.getServer()).getPrices();
        for (String cat : prices.buyCategories()) {
            builder.suggest(cat);
        }
        return builder.buildFuture();
    }
}
