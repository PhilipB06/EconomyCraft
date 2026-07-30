package com.reazip.economycraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reazip.economycraft.PriceRegistry.PriceEntry;
import com.reazip.economycraft.util.ItemArgumentCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class WorthCommand {
    private WorthCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register(CommandBuildContext buildContext) {
        return literal("worth")
                .executes(WorthCommand::worthHand)
                .then(argument("item", ItemArgument.item(buildContext))
                        .executes(ctx -> worthItem(ctx.getSource(), ItemArgument.getItem(ctx, "item"), 1))
                        .then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> worthItem(ctx.getSource(), ItemArgument.getItem(ctx, "item"),
                                        IntegerArgumentType.getInteger(ctx, "amount")))));
    }

    private static int worthHand(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) {
            source.sendFailure(Component.literal("You are not holding any item.").withStyle(ChatFormatting.RED));
            return 0;
        }

        return showWorth(source, hand, hand.getHoverName().getString(), 1);
    }

    private static int worthItem(CommandSourceStack source, ItemInput input, int amount) {
        ItemStack stack;
        try {
            stack = ItemArgumentCompat.createItemStack(input, 1);
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("Invalid item").withStyle(ChatFormatting.RED));
            return 0;
        }

        return showWorth(source, stack, stack.getHoverName().getString(), amount);
    }

    private static int showWorth(CommandSourceStack source, ItemStack stack, String itemName, int amount) {
        PriceRegistry prices = EconomyCraft.getManager(source.getServer()).getPrices();
        PriceEntry entry = prices.resolve(stack);
        if (entry == null || (entry.unitBuy() <= 0 && entry.unitSell() <= 0)) {
            source.sendFailure(Component.literal(itemName + " has no configured price.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String buy = formatBuy(entry.unitBuy(), amount);
        String sell = formatSell(entry.unitSell(), amount);
        if (buy == null || sell == null) {
            source.sendFailure(Component.literal("Amount is too large.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String prefix = amount == 1 ? itemName : amount + "x " + itemName;
        Component msg = Component.literal(prefix + " - Buy: " + buy + ", Sell: " + sell)
                .withStyle(ChatFormatting.YELLOW);

        ServerPlayer executor = tryGetPlayer(source);
        if (executor != null) {
            executor.sendSystemMessage(msg);
        } else {
            source.sendSuccess(() -> msg, false);
        }

        return 1;
    }

    @Nullable
    private static String formatBuy(long unitBuy, int amount) {
        if (unitBuy <= 0) return "not for sale";
        return formatAmount(unitBuy, amount);
    }

    @Nullable
    private static String formatSell(long unitSell, int amount) {
        if (unitSell <= 0) return "not sellable";
        return formatAmount(unitSell, amount);
    }

    @Nullable
    private static String formatAmount(long unit, int amount) {
        if (amount == 1) return EconomyCraft.formatMoney(unit);
        Long total = safeMultiply(unit, amount);
        if (total == null) return null;
        return EconomyCraft.formatMoney(total) + " (" + EconomyCraft.formatMoney(unit) + " each)";
    }

    @Nullable
    private static Long safeMultiply(long value, int amount) {
        try {
            return Math.multiplyExact(value, amount);
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    @Nullable
    private static ServerPlayer getPlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can use this command.").withStyle(ChatFormatting.RED));
            return null;
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
}
