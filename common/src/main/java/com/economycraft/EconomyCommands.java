package com.economycraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.text.Text;

import com.mojang.authlib.GameProfile;

public class EconomyCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("eco")
            .then(CommandManager.literal("balance")
                .executes(ctx -> balance(ctx.getSource(), ctx.getSource().getPlayer()))
                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                    .executes(ctx -> balance(ctx.getSource(), GameProfileArgumentType.getProfileArgument(ctx, "player").iterator().next())))
            )
            .then(CommandManager.literal("pay")
                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                    .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0))
                        .executes(ctx -> pay(ctx.getSource().getPlayer(),
                            GameProfileArgumentType.getProfileArgument(ctx, "player").iterator().next(),
                            DoubleArgumentType.getDouble(ctx, "amount")))))
            )
            .then(CommandManager.literal("addmoney")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                    .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg())
                        .executes(ctx -> {
                            return addMoney(ctx.getSource(),
                                GameProfileArgumentType.getProfileArgument(ctx, "player").iterator().next(),
                                DoubleArgumentType.getDouble(ctx, "amount"));
                        }))))
            .then(CommandManager.literal("setmoney")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                    .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg())
                        .executes(ctx -> {
                            return setMoney(ctx.getSource(),
                                GameProfileArgumentType.getProfileArgument(ctx, "player").iterator().next(),
                                DoubleArgumentType.getDouble(ctx, "amount"));
                        }))))
            .then(CommandManager.literal("leaderboard")
                .executes(ctx -> showLeaderboard(ctx.getSource())))
        );
    }

    private static int balance(ServerCommandSource source, ServerPlayerEntity player) {
        EconomyStorage storage = EconomyManager.get();
        double bal = storage.getBalance(player.getUuid());
        source.sendFeedback(() -> Text.literal("Balance: " + bal), false);
        return 1;
    }

    private static int pay(ServerPlayerEntity from, GameProfile toProfile, double amount) {
        EconomyStorage storage = EconomyManager.get();
        if (storage.getBalance(from.getUuid()) < amount) {
            from.sendMessage(Text.literal("Not enough money"), false);
            return 0;
        }
        storage.addBalance(from.getUuid(), -amount);
        storage.addBalance(toProfile.getId(), amount);
        from.sendMessage(Text.literal("Paid " + amount + " to " + toProfile.getName()), false);
        ServerPlayerEntity toPlayer = from.getServer().getPlayerManager().getPlayer(toProfile.getId());
        if (toPlayer != null) {
            toPlayer.sendMessage(Text.literal("Received " + amount + " from " + from.getEntityName()), false);
        }
        return 1;
    }

    private static int addMoney(ServerCommandSource source, GameProfile profile, double amount) {
        EconomyStorage storage = EconomyManager.get();
        storage.addBalance(profile.getId(), amount);
        source.sendFeedback(() -> Text.literal("Gave " + amount + " to " + profile.getName()), true);
        return 1;
    }

    private static int setMoney(ServerCommandSource source, GameProfile profile, double amount) {
        EconomyStorage storage = EconomyManager.get();
        storage.setBalance(profile.getId(), amount);
        source.sendFeedback(() -> Text.literal("Set balance of " + profile.getName() + " to " + amount), true);
        return 1;
    }

    private static int showLeaderboard(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective obj = scoreboard.getObjective("eco_lb");
        if (obj == null) {
            obj = scoreboard.addObjective("eco_lb", ScoreboardCriterion.DUMMY, Text.literal("Economy"), ScoreboardCriterion.RenderType.INTEGER);
        }
        scoreboard.clearObjective(obj);

        EconomyStorage storage = EconomyManager.get();
        storage.getEntriesSorted().forEach((profileId, bal) -> {
            scoreboard.getOrCreateScore(profileId.toString(), obj).setScore((int) bal.doubleValue());
        });

        scoreboard.setObjectiveSlot(1, obj); // sidebar
        return 1;
    }
}
