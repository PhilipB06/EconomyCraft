package com.reazip.economycraft;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reazip.economycraft.sell.SellUi;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import static net.minecraft.commands.Commands.literal;

public final class SellCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SellCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return literal("sell").executes(SellCommand::openSellUi);
    }

    private static int openSellUi(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can use this command.").withStyle(ChatFormatting.RED));
            return 0;
        }

        try {
            SellUi.open(player, EconomyCraft.getManager(source.getServer()));
            return 1;
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to open /sell for {}", player.getDisplayName().getString(), e);
            source.sendFailure(Component.literal("Failed to open sell menu. Check server logs."));
            return 0;
        }
    }
}
