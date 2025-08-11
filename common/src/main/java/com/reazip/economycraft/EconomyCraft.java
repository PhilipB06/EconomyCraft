package com.reazip.economycraft;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;

import java.text.NumberFormat;
import java.util.Locale;

public final class EconomyCraft {
    public static final String MOD_ID = "economycraft";
    private static EconomyManager manager;
    private static MinecraftServer lastServer;
    private static final NumberFormat FORMAT = NumberFormat.getInstance(Locale.GERMANY);

    public static void init() {
        EconomyConfig.load(null);
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            EconomyCommands.register(dispatcher);
        });

        LifecycleEvent.SERVER_STARTED.register(server -> {
            getManager(server); // load balances
        });
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            if (manager != null && lastServer == server) {
                manager.save();
            }
        });

        PlayerEvent.PLAYER_JOIN.register(player -> {
            EconomyManager eco = getManager(player.getServer());
            eco.getBalance(player.getUUID());
            if (eco.getOrders().hasDeliveries(player.getUUID()) || eco.getShop().hasDeliveries(player.getUUID())) {
                Component msg = Component.literal("You have unclaimed items: ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("[Claim]")
                                .withStyle(s -> s.withUnderlined(true)
                                        .withColor(ChatFormatting.GREEN)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/eco orders claim"))));
                player.sendSystemMessage(msg);
            }
        });
    }

    public static EconomyManager getManager(MinecraftServer server) {
        if (manager == null || lastServer != server) {
            manager = new EconomyManager(server);
            lastServer = server;
        }
        return manager;
    }

    public static String formatMoney(long amount) {
        return "$" + FORMAT.format(amount);
    }
}
