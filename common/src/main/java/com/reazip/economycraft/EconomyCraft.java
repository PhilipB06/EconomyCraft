package com.reazip.economycraft;

import com.reazip.economycraft.util.ChatCompat;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.text.NumberFormat;
import java.util.Locale;

public final class EconomyCraft {
    public static final String MOD_ID = "economycraft";
    private static EconomyManager manager;
    private static MinecraftServer lastServer;
    private static final NumberFormat FORMAT = NumberFormat.getInstance(Locale.GERMANY);

    public static void registerEvents() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            EconomyCommands.register(dispatcher);
        });

        LifecycleEvent.SERVER_STARTED.register(EconomyCraft::getManager);

        LifecycleEvent.SERVER_STOPPING.register(server -> {
            if (manager != null && lastServer == server) {
                manager.save();
            }
        });

        PlayerEvent.PLAYER_JOIN.register(EconomyCraft::onPlayerJoin);
    }

    private static void onPlayerJoin(ServerPlayer player) {
        EconomyManager eco = getManager(player.level().getServer());
        eco.getBalance(player.getUUID(), true);

        if (eco.getOrders().hasDeliveries(player.getUUID()) || eco.getShop().hasDeliveries(player.getUUID())) {
            ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");

            if (ev != null) {
                Component msg = Component.literal("You have unclaimed items: ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("[Claim]")
                                .withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev)));
                player.sendSystemMessage(msg);
            } else {
                ChatCompat.sendRunCommandTellraw(
                        player,
                        "You have unclaimed items: ",
                        "[Claim]",
                        "/eco orders claim"
                );
            }
        }
    }

    public static EconomyManager getManager(MinecraftServer server) {
        if (manager == null || lastServer != server) {
            manager = new EconomyManager(server);
            lastServer = server;
        }
        return manager;
    }

    public static Component createBalanceTitle(String baseTitle, ServerPlayer player) {
        EconomyManager eco = getManager(player.level().getServer());
        long balance = eco.getBalance(player.getUUID(), true);
        return Component.literal(baseTitle + " - Balance: " + formatMoney(balance));
    }

    public static String formatMoney(long amount) {
        return "$" + FORMAT.format(amount);
    }
}
