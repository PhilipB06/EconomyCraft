package com.reazip.economycraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.logging.LogUtils;
import com.reazip.economycraft.admin.AdminUi;
import com.reazip.economycraft.util.AsyncFileWriter;
import com.reazip.economycraft.util.EconomyPaths;
import com.reazip.economycraft.util.EconomySounds;
import com.reazip.economycraft.util.ExpirationUtil;
import com.reazip.economycraft.util.EconomyPermissions;
import com.reazip.economycraft.util.EconomyPermissions.Nodes;
import com.reazip.economycraft.util.IdentityCompat;
import com.reazip.economycraft.util.ItemArgumentCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.*;

import java.util.concurrent.CompletableFuture;
import com.reazip.economycraft.auction.AuctionListing;
import com.reazip.economycraft.auction.AuctionManager;
import com.reazip.economycraft.auction.AuctionUi;
import com.reazip.economycraft.shop.ShopUi;
import com.reazip.economycraft.orders.OrderFulfillment;
import com.reazip.economycraft.orders.OrderManager;
import com.reazip.economycraft.orders.OrderRequest;
import com.reazip.economycraft.orders.OrdersUi;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import org.jetbrains.annotations.Nullable;

public final class EconomyCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAIN_INVENTORY_SLOTS = 36;
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext,
                                Commands.CommandSelection selection) {
        dispatcher.register(buildRoot(
                buildContext,
                selection,
                buildAddMoney(),
                buildSetMoney(),
                buildRemoveMoney(),
                buildRemovePlayer()
        ));

        dispatcher.register(withCommandPermission(
                buildBalance().requires(s -> EconomyConfig.get().standaloneCommands), Nodes.COMMAND_BALANCE));
        dispatcher.register(withCommandPermission(
                buildPay().requires(s -> EconomyConfig.get().standaloneCommands), Nodes.COMMAND_PAY));
        dispatcher.register(withCommandPermission(
                SellCommand.register().requires(s -> EconomyConfig.get().standaloneCommands && EconomyConfig.get().sellEnabled),
                Nodes.COMMAND_SELL));
        registerStandalone(dispatcher, buildAuction("ah"), Nodes.COMMAND_AUCTION);
        registerStandalone(dispatcher, buildAuction("auction"), Nodes.COMMAND_AUCTION);
        registerStandalone(dispatcher, buildShop(), Nodes.COMMAND_SHOP);
        dispatcher.register(withCommandPermission(
                buildOrders(buildContext).requires(s -> EconomyConfig.get().standaloneCommands), Nodes.COMMAND_ORDERS));
        dispatcher.register(withCommandPermission(
                buildDeliveries().requires(s -> EconomyConfig.get().standaloneCommands), Nodes.COMMAND_DELIVERIES));
        dispatcher.register(withCommandPermission(
                buildDaily().requires(s -> EconomyConfig.get().standaloneCommands), Nodes.COMMAND_DAILY));
        dispatcher.register(withCommandPermission(
                buildTransactions().requires(s -> EconomyConfig.get().standaloneCommands), Nodes.COMMAND_TRANSACTIONS));
        dispatcher.register(withCommandPermission(
                WorthCommand.register(buildContext).requires(s ->
                        EconomyConfig.get().standaloneCommands && EconomyConfig.get().worthEnabled),
                Nodes.COMMAND_WORTH));

        dispatcher.register(
                buildAddMoney().requires(src ->
                        EconomyPermissions.checkAdmin(src, Nodes.ADMIN_PLAYERS)
                                && EconomyConfig.get().standaloneAdminCommands
                )
        );

        dispatcher.register(
                buildSetMoney().requires(src ->
                        EconomyPermissions.checkAdmin(src, Nodes.ADMIN_PLAYERS)
                                && EconomyConfig.get().standaloneAdminCommands
                )
        );

        dispatcher.register(
                buildRemoveMoney().requires(src ->
                        EconomyPermissions.checkAdmin(src, Nodes.ADMIN_PLAYERS)
                                && EconomyConfig.get().standaloneAdminCommands
                )
        );

        dispatcher.register(
                buildRemovePlayer().requires(src ->
                        EconomyPermissions.checkAdmin(src, Nodes.ADMIN_PLAYERS)
                                && EconomyConfig.get().standaloneAdminCommands
                )
        );

    }

    private static void registerStandalone(CommandDispatcher<CommandSourceStack> dispatcher,
                                           LiteralArgumentBuilder<CommandSourceStack> command, String node) {
        withCommandPermission(command, node);
        command.requires(command.getRequirement().and(src -> EconomyConfig.get().standaloneCommands));
        dispatcher.register(command);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> withCommandPermission(
            LiteralArgumentBuilder<CommandSourceStack> command, String node) {
        command.requires(command.getRequirement().and(src -> EconomyPermissions.checkCommand(src, node)));
        return command;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(
            CommandBuildContext buildContext,
            Commands.CommandSelection selection,
            LiteralArgumentBuilder<CommandSourceStack> addMoney,
            LiteralArgumentBuilder<CommandSourceStack> setMoney,
            LiteralArgumentBuilder<CommandSourceStack> removeMoney,
            LiteralArgumentBuilder<CommandSourceStack> removePlayer
    ) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("eco");

        root.executes(ctx -> openHub(ctx.getSource()));
        root.then(literal("menu")
                .requires(src -> EconomyPermissions.checkCommand(src, Nodes.COMMAND_MENU))
                .executes(ctx -> openHub(ctx.getSource())));
        root.then(literal("admin").requires(EconomyPermissions::hasAnyAdmin)
                .executes(ctx -> openAdmin(ctx.getSource())));

        root.then(withCommandPermission(buildBalance(), Nodes.COMMAND_BALANCE));
        root.then(withCommandPermission(buildPay(), Nodes.COMMAND_PAY));
        root.then(withCommandPermission(
                SellCommand.register().requires(s -> EconomyConfig.get().sellEnabled), Nodes.COMMAND_SELL));
        root.then(withCommandPermission(buildAuction("ah"), Nodes.COMMAND_AUCTION));
        root.then(withCommandPermission(buildAuction("auction"), Nodes.COMMAND_AUCTION));
        root.then(withCommandPermission(buildShop(), Nodes.COMMAND_SHOP));
        root.then(withCommandPermission(buildOrders(buildContext), Nodes.COMMAND_ORDERS));
        root.then(withCommandPermission(buildDeliveries(), Nodes.COMMAND_DELIVERIES));
        root.then(withCommandPermission(buildDaily(), Nodes.COMMAND_DAILY));
        root.then(withCommandPermission(buildTransactions(), Nodes.COMMAND_TRANSACTIONS));
        root.then(withCommandPermission(
                WorthCommand.register(buildContext).requires(s -> EconomyConfig.get().worthEnabled), Nodes.COMMAND_WORTH));

        root.then(addMoney);
        root.then(setMoney);
        root.then(removeMoney);
        root.then(removePlayer);

        if (selection != Commands.CommandSelection.DEDICATED) {
            root.then(literal("import")
                    .requires(s -> EconomyCraft.canImportSharedFolder())
                    .executes(ctx -> importSharedFolder(ctx.getSource())));
        }

        return root;
    }

    private static int importSharedFolder(CommandSourceStack source) {
        MinecraftServer server = source.getServer();

        if (!EconomyPaths.hasSharedFolder(server)) {
            source.sendFailure(Component.literal("There is nothing left to import.").withStyle(ChatFormatting.RED));
            return 0;
        }

        AsyncFileWriter.flush();

        if (!EconomyPaths.importSharedFolder(server)) {
            source.sendFailure(Component.literal("Import failed. config/economycraft was left in place, so you can try again. Check the log for the reason.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyCraft.reloadFromDisk(server);
        resyncCommands(server);

        source.sendSuccess(() -> Component.literal("Imported the old settings, prices and economy into this world. The old folder is now config/economycraft_imported.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static void resyncCommands(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(player);
        }
    }

    private static int openHub(CommandSourceStack source) {
        ServerPlayer player = tryGetPlayer(source);
        if (player == null) {
            source.sendFailure(Component.literal("Only players can open the menu.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!EconomyPermissions.checkCommand(source, Nodes.COMMAND_MENU)) {
            source.sendFailure(Component.literal("You don't have permission for that.").withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            HubUi.open(player);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to open the menu for {}", player.getDisplayName().getString(), e);
            source.sendFailure(Component.literal("Failed to open the menu. Check server logs."));
            return 0;
        }
    }

    private static int openAdmin(CommandSourceStack source) {
        ServerPlayer player = tryGetPlayer(source);
        if (player == null) {
            source.sendFailure(Component.literal("Only players can open the admin menu.").withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            AdminUi.open(player, EconomyCraft.getManager(source.getServer()));
            return 1;
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to open the admin menu for {}", player.getDisplayName().getString(), e);
            source.sendFailure(Component.literal("Failed to open the admin menu. Check server logs."));
            return 0;
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildBalance() {
        return literal("bal")
                .executes(ctx -> showBalance(IdentityCompat.of(ctx.getSource().getPlayerOrException()), ctx.getSource()))
                .then(argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestPlayers(ctx.getSource(), builder))
                        .executes(ctx -> showBalance(StringArgumentType.getString(ctx, "target"), ctx.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildPay() {
        String payUsage = "/pay <player> <amount>";
        return literal("pay")
                .executes(ctx -> usage(ctx.getSource(), payUsage))
                .then(argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestPlayers(ctx.getSource(), builder))
                        .executes(ctx -> usage(ctx.getSource(), payUsage))
                        .then(argument("amount", StringArgumentType.word())
                                .executes(ctx -> {
                                    Long amount = parseAmount(ctx.getSource(), StringArgumentType.getString(ctx, "amount"), 1, EconomyManager.MAX);
                                    if (amount == null) return 0;
                                    return pay(ctx.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(ctx, "player"),
                                            amount, ctx.getSource());
                                })));
    }

    private static int usage(CommandSourceStack source, String usage) {
        source.sendFailure(Component.literal("Usage: " + usage).withStyle(ChatFormatting.RED));
        return 0;
    }

    private static @Nullable Long parseAmount(CommandSourceStack source, String raw, long min, long max) {
        Long amount = EconomyCraft.parseMoneyShort(raw);
        if (amount == null) {
            source.sendFailure(Component.literal("Invalid amount: " + raw + " (try 1000, 1.5k, 20k, 234M, ...)")
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        if (amount < min || amount > max) {
            source.sendFailure(Component.literal("Amount must be between " + min + " and " + max)
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        return amount;
    }

    private static int showBalance(IdentityCompat.PlayerRef target, CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        Long bal = manager.getBalance(target.id(), false);
        if (bal == null) {
            source.sendFailure(Component.literal("Unknown player").withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerPlayer executor = tryGetPlayer(source);

        Component msg;
        if (executor != null && executor.getUUID().equals(target.id())) {
            msg = Component.literal("Balance: " + EconomyCraft.formatMoney(bal))
                    .withStyle(ChatFormatting.YELLOW);
        } else {
            msg = Component.literal(target.name() + "'s balance: " + EconomyCraft.formatMoney(bal))
                    .withStyle(ChatFormatting.YELLOW);
        }

        reply(source, executor, msg, false);

        return 1;
    }

    private static int showBalance(String targetName, CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        UUID targetId = manager.tryResolveUuidByName(targetName);
        if (targetId == null) {
            source.sendFailure(Component.literal("Unknown player").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (manager.getBalance(targetId, false) == null) {
            source.sendFailure(Component.literal("Unknown player").withStyle(ChatFormatting.RED));
            return 0;
        }

        String resolvedName = manager.getBestName(targetId);
        return showBalance(new IdentityCompat.PlayerRef(targetId,
                resolvedName == null || resolvedName.isBlank() ? targetName : resolvedName), source);
    }

    private static int pay(ServerPlayer from, String target, long amount, CommandSourceStack source) {
        var server = source.getServer();
        EconomyManager manager = EconomyCraft.getManager(server);

        ServerPlayer toOnline = server.getPlayerList().getPlayerByName(target);
        UUID toId = (toOnline != null) ? toOnline.getUUID() : null;

        if (toId == null) {
            try { toId = UUID.fromString(target); } catch (IllegalArgumentException ignored) {}
        }

        if (toId == null) {
            toId = manager.tryResolveUuidByName(target);
        }

        if (toId == null) {
            EconomySounds.failure(from);
            source.sendFailure(Component.literal("Unknown player").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (from.getUUID().equals(toId)) {
            EconomySounds.failure(from);
            source.sendFailure(Component.literal("You cannot pay yourself").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (manager.getBalance(toId, false) == null) {
            EconomySounds.failure(from);
            source.sendFailure(Component.literal("Unknown player").withStyle(ChatFormatting.RED));
            return 0;
        }

        String displayName = toOnline != null ? IdentityCompat.of(toOnline).name() : manager.getBestName(toId);
        if (displayName == null || displayName.isBlank()) {
            EconomySounds.failure(from);
            source.sendFailure(Component.literal("Unknown player").withStyle(ChatFormatting.RED));
            return 0;
        }

        var payment = manager.pay(from.getUUID(), toId, amount, EconomySources.PLAYER_PAYMENT);
        if (payment.successful()) {

            ServerPlayer executor = tryGetPlayer(source);
            if (executor != null) EconomySounds.success(executor);

            Component msg = Component.literal("Paid " + EconomyCraft.formatMoney(amount) + " to " + displayName)
                    .withStyle(ChatFormatting.GREEN);

            reply(source, executor, msg, false);

            if (toOnline != null) {
                EconomySounds.moneyReceived(toOnline);
                toOnline.sendSystemMessage(
                        Component.literal(from.getName().getString() + " sent you " + EconomyCraft.formatMoney(amount))
                                .withStyle(ChatFormatting.GREEN)
                );
            }
        } else {
            EconomySounds.failure(from);
            String message = payment.status() == com.reazip.economycraft.api.v1.BalanceMutationStatus.MAX_BALANCE_EXCEEDED
                    ? "Recipient cannot receive that much money"
                    : "Not enough balance";
            source.sendFailure(Component.literal(message).withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAddMoney() {
        return literal("addmoney").requires(src -> EconomyPermissions.checkAdmin(src, Nodes.ADMIN_PLAYERS))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .then(argument("amount", StringArgumentType.word())
                                .executes(ctx -> {
                                    Long amount = parseAmount(ctx.getSource(), StringArgumentType.getString(ctx, "amount"), 1, EconomyManager.MAX);
                                    if (amount == null) return 0;
                                    return addMoney(
                                            IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                            amount,
                                            ctx.getSource());
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSetMoney() {
        return literal("setmoney").requires(src -> EconomyPermissions.checkAdmin(src, Nodes.ADMIN_PLAYERS))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .then(argument("amount", StringArgumentType.word())
                                .executes(ctx -> {
                                    Long amount = parseAmount(ctx.getSource(), StringArgumentType.getString(ctx, "amount"), 0, EconomyManager.MAX);
                                    if (amount == null) return 0;
                                    return setMoney(
                                            IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                            amount,
                                            ctx.getSource());
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRemoveMoney() {
        return literal("removemoney").requires(src -> EconomyPermissions.checkAdmin(src, Nodes.ADMIN_PLAYERS))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .executes(ctx -> removeMoney(
                                IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                null,
                                ctx.getSource()))
                        .then(argument("amount", StringArgumentType.word())
                                .executes(ctx -> {
                                    Long amount = parseAmount(ctx.getSource(), StringArgumentType.getString(ctx, "amount"), 1, EconomyManager.MAX);
                                    if (amount == null) return 0;
                                    return removeMoney(
                                            IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                            amount,
                                            ctx.getSource());
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRemovePlayer() {
        return literal("removeplayer").requires(src -> EconomyPermissions.checkAdmin(src, Nodes.ADMIN_PLAYERS))
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
        ServerPlayer executor = tryGetPlayer(source);

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            var result = manager.addMoney(p.id(), amount, EconomySources.ADMIN_ADD);
            if (!result.successful()) {
                source.sendFailure(Component.literal("Could not add money: maximum balance exceeded")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            Component msg = Component.literal(
                            "Added " + EconomyCraft.formatMoney(amount) + " to " + p.name() + "'s balance.")
                    .withStyle(ChatFormatting.GREEN);

            reply(source, executor, msg, true);

            return 1;
        }

        int count = 0;
        for (var p : profiles) {
            if (manager.addMoney(p.id(), amount, EconomySources.ADMIN_ADD).successful()) count++;
        }

        if (count == 0) {
            source.sendFailure(Component.literal("Could not add money: all target balances would exceed the maximum")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        Component msg = Component.literal(
                        "Added " + EconomyCraft.formatMoney(amount) + " to " + count + " player" + (count > 1 ? "s" : ""))
                .withStyle(ChatFormatting.GREEN);

        reply(source, executor, msg, true);

        return count;
    }

    private static int setMoney(Collection<IdentityCompat.PlayerRef> profiles, long amount, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        ServerPlayer executor = tryGetPlayer(source);

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            manager.setMoney(p.id(), amount, EconomySources.ADMIN_SET);

            Component msg = Component.literal(
                            "Set balance of " + p.name() + " to " + EconomyCraft.formatMoney(amount))
                    .withStyle(ChatFormatting.GREEN);

            reply(source, executor, msg, true);

            return 1;
        }

        for (var p : profiles) {
            manager.setMoney(p.id(), amount, EconomySources.ADMIN_SET);
        }

        int count = profiles.size();

        Component msg = Component.literal(
                        "Set balance to " + EconomyCraft.formatMoney(amount) + " for " + count + " player" + (count > 1 ? "s" : ""))
                .withStyle(ChatFormatting.GREEN);

        reply(source, executor, msg, true);

        return count;
    }

    private static int removeMoney(Collection<IdentityCompat.PlayerRef> profiles, Long amount, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        ServerPlayer executor = tryGetPlayer(source);

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
                manager.setMoney(id, 0L, EconomySources.ADMIN_REMOVE);
                Component msg = Component.literal(
                                "Removed all money from " + p.name() + "'s balance.")
                        .withStyle(ChatFormatting.GREEN);
                reply(source, executor, msg, true);
                return 1;
            }

            if (!manager.removeMoney(id, amount, EconomySources.ADMIN_REMOVE).successful()) {
                source.sendFailure(Component.literal(
                                "Failed to remove " + EconomyCraft.formatMoney(amount) + " from " + p.name() + "'s balance due to insufficient funds.")
                        .withStyle(ChatFormatting.RED));
                return 1;
            }

            Component msg = Component.literal(
                            "Successfully removed " + EconomyCraft.formatMoney(amount) + " from " + p.name() + "'s balance.")
                    .withStyle(ChatFormatting.GREEN);
            reply(source, executor, msg, true);
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
                manager.setMoney(id, 0L, EconomySources.ADMIN_REMOVE);
                success++;
            } else {
                if (manager.removeMoney(id, amount, EconomySources.ADMIN_REMOVE).successful()) {
                    success++;
                } else {
                    source.sendFailure(Component.literal(
                                    "Failed to remove " + EconomyCraft.formatMoney(amount) + " from " + p.name() + "'s balance due to insufficient funds.")
                            .withStyle(ChatFormatting.RED));
                }
            }
        }

        if (success > 0) {
            Component msg;
            if (amount == null) {
                msg = Component.literal(
                                "Removed all money from " + success + " player" + (success > 1 ? "s" : "") + ".")
                        .withStyle(ChatFormatting.GREEN);
            } else {
                msg = Component.literal(
                                "Successfully removed " + EconomyCraft.formatMoney(amount) + " from " + success + " player" + (success > 1 ? "s" : "") + ".")
                        .withStyle(ChatFormatting.GREEN);
            }
            reply(source, executor, msg, true);
        }

        return profiles.size();
    }

    private static int removePlayers(Collection<IdentityCompat.PlayerRef> profiles, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        ServerPlayer executor = tryGetPlayer(source);

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            manager.removePlayer(p.id());

            Component msg = Component.literal("Removed " + p.name() + " from economy")
                    .withStyle(ChatFormatting.GREEN);

            reply(source, executor, msg, true);

            return 1;
        }

        for (var p : profiles) {
            manager.removePlayer(p.id());
        }

        int count = profiles.size();

        Component msg = Component.literal(
                        "Removed " + count + " player" + (count > 1 ? "s" : "") + " from economy")
                .withStyle(ChatFormatting.GREEN);

        reply(source, executor, msg, true);

        return count;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAuction(String command) {
        return literal(command)
                .requires(src -> EconomyConfig.get().auctionEnabled)
                .executes(ctx -> openAuction(ctx.getSource().getPlayerOrException(), ctx.getSource()))
                .then(literal("list")
                        .executes(ctx -> usage(ctx.getSource(), "/" + command + " list <price> [<amount>]"))
                        .then(argument("price", LongArgumentType.longArg(1, EconomyManager.MAX))
                                .executes(ctx -> listAuctionItem(ctx.getSource().getPlayerOrException(),
                                         LongArgumentType.getLong(ctx, "price"), -1,
                                         ctx.getSource()))
                                .then(argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> listAuctionItem(ctx.getSource().getPlayerOrException(),
                                                 LongArgumentType.getLong(ctx, "price"),
                                                 IntegerArgumentType.getInteger(ctx, "amount"),
                                                 ctx.getSource())))))
                .then(literal("search")
                        .executes(ctx -> usage(ctx.getSource(), "/" + command + " search <query>"))
                        .then(argument("query", StringArgumentType.greedyString())
                                .executes(ctx -> searchAuction(ctx.getSource().getPlayerOrException(),
                                         StringArgumentType.getString(ctx, "query"),
                                         ctx.getSource()))));
    }

    private static int openAuction(ServerPlayer player, CommandSourceStack source) {
        if (!EconomyConfig.get().auctionEnabled) {
            source.sendFailure(Component.literal("The auction house is disabled.").withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            AuctionUi.open(player, EconomyCraft.getManager(source.getServer()).getAuctions());
            return 1;
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to open the auction house for {}", player.getDisplayName().getString(), e);
            source.sendFailure(Component.literal("Failed to open auction house. Check server logs."));
            return 0;
        }
    }

    private static int searchAuction(ServerPlayer player, String query, CommandSourceStack source) {
        if (!EconomyConfig.get().auctionEnabled) {
            source.sendFailure(Component.literal("The auction house is disabled.").withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            AuctionUi.openSearch(player, EconomyCraft.getManager(source.getServer()).getAuctions(), query);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to search the auction house for {}", player.getDisplayName().getString(), e);
            source.sendFailure(Component.literal("Failed to open auction house. Check server logs."));
            return 0;
        }
    }

    private static int listAuctionItem(ServerPlayer player, long price, int amount, CommandSourceStack source) {
        if (!EconomyConfig.get().auctionEnabled) {
            EconomySounds.failure(player);
            source.sendFailure(Component.literal("The auction house is disabled.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) {
            EconomySounds.failure(player);
            source.sendFailure(Component.literal("Hold the item to list in your hand").withStyle(ChatFormatting.RED));
            return 0;
        }

        int count = amount > 0 ? amount : Math.min(hand.getCount(), hand.getMaxStackSize());
        if (count > hand.getCount()) {
            EconomySounds.failure(player);
            source.sendFailure(Component.literal("You only have " + hand.getCount() + ".").withStyle(ChatFormatting.RED));
            return 0;
        }

        AuctionManager auctions = EconomyCraft.getManager(source.getServer()).getAuctions();
        if (auctions.hasReachedLimit(player.getUUID())) {
            EconomySounds.failure(player);
            source.sendFailure(Component.literal("You have reached your limit of "
                    + auctions.getEffectiveLimit(player.getUUID()) + " active listing(s).").withStyle(ChatFormatting.RED));
            return 0;
        }
        AuctionListing listing = new AuctionListing();
        listing.seller = player.getUUID();
        listing.price = price;
        listing.item = hand.copyWithCount(count);
        listing.createdAt = System.currentTimeMillis();
        listing.expiresAt = ExpirationUtil.expiresAt(listing.createdAt, EconomyConfig.get().auctionExpirationHours);
        hand.shrink(count);
        auctions.addListing(listing);

        long tax = Math.round(price * EconomyConfig.get().taxRate);

        Component msg = Component.literal("Listed item for " + EconomyCraft.formatMoney(price) +
                        (tax > 0 ? " (buyers pay " + EconomyCraft.formatMoney(price + tax) + ")" : ""))
                .withStyle(ChatFormatting.GREEN);

        EconomySounds.success(player);
        player.sendSystemMessage(msg);

        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildShop() {
        return literal("shop")
                .requires(src -> EconomyConfig.get().shopEnabled)
                .executes(ctx -> openShop(ctx.getSource().getPlayerOrException(), ctx.getSource(), null))
                .then(argument("category", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> suggestShopCategories(ctx.getSource(), builder))
                        .executes(ctx -> openShop(
                                ctx.getSource().getPlayerOrException(),
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "category")
                        )))
                .then(literal("search")
                        .executes(ctx -> usage(ctx.getSource(), "/shop search <query>"))
                        .then(argument("query", StringArgumentType.greedyString())
                                .executes(ctx -> searchShop(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "query"),
                                        ctx.getSource()))));
    }

    private static int openShop(ServerPlayer player, CommandSourceStack source, @Nullable String category) {
        if (!EconomyConfig.get().shopEnabled) {
            source.sendFailure(Component.literal("Shop is disabled.").withStyle(ChatFormatting.RED));
            return 0;
        }
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        try {
            ShopUi.open(player, manager, category);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to open /shop for {} (category={})",
                    player.getDisplayName().getString(), category, e);
            source.sendFailure(Component.literal("Failed to open shop. Check server logs."));
            return 0;
        }
    }

    private static int searchShop(ServerPlayer player, String query, CommandSourceStack source) {
        if (!EconomyConfig.get().shopEnabled) {
            source.sendFailure(Component.literal("Shop is disabled.").withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            ShopUi.openSearch(player, EconomyCraft.getManager(source.getServer()), query);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to search /shop for {}", player.getDisplayName().getString(), e);
            source.sendFailure(Component.literal("Failed to open shop. Check server logs."));
            return 0;
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildOrders(CommandBuildContext buildContext) {
        String requestUsage = "/orders request <item> <amount> <price>";
        return literal("orders")
                .executes(ctx -> openOrders(ctx.getSource().getPlayerOrException(), ctx.getSource()))
                .then(literal("request")
                        .requires(src -> EconomyConfig.get().ordersEnabled)
                        .executes(ctx -> usage(ctx.getSource(), requestUsage))
                        .then(argument("item", ItemArgument.item(buildContext))
                                .executes(ctx -> usage(ctx.getSource(), requestUsage))
                                .then(argument("amount", LongArgumentType.longArg(1, EconomyManager.MAX))
                                        .executes(ctx -> usage(ctx.getSource(), requestUsage))
                                        .then(argument("price", LongArgumentType.longArg(1, EconomyManager.MAX))
                                                .executes(ctx -> requestItem(ctx.getSource().getPlayerOrException(),
                                                        ItemArgument.getItem(ctx, "item"),
                                                        (int) Math.min(LongArgumentType.getLong(ctx, "amount"), EconomyManager.MAX),
                                                        LongArgumentType.getLong(ctx, "price"),
                                                        ctx.getSource()))))))
                .then(literal("search")
                        .requires(src -> EconomyConfig.get().ordersEnabled)
                        .executes(ctx -> usage(ctx.getSource(), "/orders search <query>"))
                        .then(argument("query", StringArgumentType.greedyString())
                                .executes(ctx -> searchOrders(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "query"),
                                        ctx.getSource()))));
    }

    private static int openOrders(ServerPlayer player, CommandSourceStack source) {
        if (!EconomyConfig.get().ordersEnabled) {
            source.sendFailure(Component.literal("Orders are disabled.").withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            OrdersUi.open(player, EconomyCraft.getManager(source.getServer()));
            return 1;
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to open /orders for {}", player.getDisplayName().getString(), e);
            source.sendFailure(Component.literal("Failed to open orders. Check server logs."));
            return 0;
        }
    }

    private static int requestItem(ServerPlayer player, ItemInput input, int amount, long price, CommandSourceStack source) {
        ItemStack item;
        try {
            item = ItemArgumentCompat.createItemStack(input, 1);
        } catch (CommandSyntaxException e) {
            EconomySounds.failure(player);
            source.sendFailure(Component.literal("Invalid item").withStyle(ChatFormatting.RED));
            return 0;
        }
        EconomyManager eco = EconomyCraft.getManager(source.getServer());
        OrderManager orders = eco.getOrders();
        if (orders.hasReachedLimit(player.getUUID())) {
            EconomySounds.failure(player);
            source.sendFailure(Component.literal("You have reached your limit of "
                    + orders.getEffectiveLimit(player.getUUID()) + " active order request(s).").withStyle(ChatFormatting.RED));
            return 0;
        }
        int maxAmount = MAIN_INVENTORY_SLOTS * item.getMaxStackSize();
        if (amount > maxAmount) {
            EconomySounds.failure(player);
            source.sendFailure(Component.literal("Amount exceeds " + MAIN_INVENTORY_SLOTS + " stacks (max " + maxAmount + ")").withStyle(ChatFormatting.RED));
            return 0;
        }

        OrderRequest r = OrderFulfillment.createEscrowedRequest(eco, player.getUUID(), item, amount, price);
        if (r == null) {
            EconomySounds.failure(player);
            source.sendFailure(Component.literal("You can't afford to reserve " + EconomyCraft.formatMoney(price)).withStyle(ChatFormatting.RED));
            return 0;
        }
        long tax = Math.round(price * EconomyConfig.get().taxRate);

        Component msg = Component.literal("Created request" +
                (tax > 0 ? " (fulfiller receives " + EconomyCraft.formatMoney(price - tax) + ")" : ""))
                .withStyle(ChatFormatting.GREEN);
        EconomySounds.success(player);
        player.sendSystemMessage(msg);

        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDeliveries() {
        return literal("deliveries")
                .executes(ctx -> openDeliveries(ctx.getSource().getPlayerOrException(), ctx.getSource()));
    }

    private static int openDeliveries(ServerPlayer player, CommandSourceStack source) {
        OrdersUi.openClaims(player, EconomyCraft.getManager(source.getServer()));
        return 1;
    }

    private static int searchOrders(ServerPlayer player, String query, CommandSourceStack source) {
        if (!EconomyConfig.get().ordersEnabled) {
            source.sendFailure(Component.literal("Orders are disabled.").withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            OrdersUi.openSearch(player, EconomyCraft.getManager(source.getServer()), query);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to search /orders for {}", player.getDisplayName().getString(), e);
            source.sendFailure(Component.literal("Failed to open orders. Check server logs."));
            return 0;
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDaily() {
        return literal("daily")
                .executes(ctx -> daily(ctx.getSource().getPlayerOrException(), ctx.getSource()));
    }

    private static int daily(ServerPlayer player, CommandSourceStack source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        boolean alreadyClaimed = manager.hasClaimedDailyToday(player.getUUID());
        if (manager.claimDaily(player.getUUID())) {
            EconomySounds.dailyReward(player);
            Component msg = Component.literal("Claimed " + EconomyCraft.formatMoney(EconomyConfig.get().dailyAmount))
                    .withStyle(ChatFormatting.GREEN);
            player.sendSystemMessage(msg);
        } else if (alreadyClaimed) {
            EconomySounds.failure(player);
            source.sendFailure(Component.literal("Already claimed today").withStyle(ChatFormatting.RED));
        } else {
            EconomySounds.failure(player);
            source.sendFailure(Component.literal("Daily reward could not be added to your balance")
                    .withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTransactions() {
        return literal("transactions")
                .executes(ctx -> openTransactions(ctx.getSource().getPlayerOrException(), ctx.getSource()));
    }

    private static int openTransactions(ServerPlayer player, CommandSourceStack source) {
        try {
            TransactionsUi.open(player);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to open /transactions for {}", player.getDisplayName().getString(), e);
            source.sendFailure(Component.literal("Failed to open transactions. Check server logs."));
            return 0;
        }
    }

    @Nullable
    private static ServerPlayer tryGetPlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception e) {
            return null;
        }
    }

    private static void reply(CommandSourceStack source, @Nullable ServerPlayer executor, Component msg, boolean broadcastToOps) {
        if (executor != null) {
            executor.sendSystemMessage(msg);
        } else {
            source.sendSuccess(() -> msg, broadcastToOps);
        }
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandSourceStack source, SuggestionsBuilder builder) {
        var server = source.getServer();
        var manager = EconomyCraft.getManager(server);
        Set<String> suggestions = new HashSet<>();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            suggestions.add(IdentityCompat.of(p).name());
        }

        for (UUID id : manager.getBalances().keySet()) {
            String name = manager.getBestName(id);
            if (name != null && !name.isBlank()) {
                suggestions.add(name);
            }
        }

        String typed = builder.getRemainingLowerCase();
        suggestions.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(typed))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestShopCategories(CommandSourceStack source, SuggestionsBuilder builder) {
        PriceRegistry prices = EconomyCraft.getManager(source.getServer()).getPrices();
        for (String cat : prices.buyCategories()) {
            builder.suggest(cat);
        }
        return builder.buildFuture();
    }
}
