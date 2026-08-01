package com.reazip.economycraft;

import com.mojang.logging.LogUtils;
import com.reazip.economycraft.util.AsyncFileWriter;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.EconomyPaths;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class EconomyCraft {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "economycraft";
    private static EconomyManager manager;
    private static MinecraftServer lastServer;

    public static void registerEvents() {
        LifecycleEvent.SERVER_STARTING.register(EconomyConfig::load);

        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            EconomyCommands.register(dispatcher, registry, selection);
        });

        LifecycleEvent.SERVER_STARTED.register(EconomyCraft::getManager);

        LifecycleEvent.SERVER_STOPPING.register(server -> {
            if (manager != null && lastServer == server) {
                manager.save();
            }
            AsyncFileWriter.flush();
        });

        PlayerEvent.PLAYER_JOIN.register(EconomyCraft::onPlayerJoin);
    }

    private static void onPlayerJoin(ServerPlayer player) {
        try {
            MinecraftServer server = player.level().getServer();
            EconomyManager eco = getManager(server);
            eco.getBalance(player.getUUID(), true);

            if (eco.getDeliveries().hasDeliveries(player.getUUID())) {
                sendPrompt(player, "You have unclaimed items: ", "[Claim]", "/eco orders claim");
            }

            if (EconomyPaths.hasSharedFolder(server)) {
                sendPrompt(player, "Found an older EconomyCraft setup. ", "[Import]", "/eco import");
            }
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to set up {} on join", player.getName().getString(), e);
        }
    }

    private static void sendPrompt(ServerPlayer player, String text, String label, String command) {
        ClickEvent ev = ChatCompat.runCommandEvent(command);

        if (ev != null) {
            Component msg = Component.literal(text)
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(label)
                            .withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev)));
            player.sendSystemMessage(msg);
        } else {
            ChatCompat.sendRunCommandTellraw(player, text, label, command);
        }
    }

    public static EconomyManager getManager(MinecraftServer server) {
        if (manager == null || lastServer != server) {
            manager = new EconomyManager(server);
            lastServer = server;
        }
        return manager;
    }

    public static boolean canImportSharedFolder() {
        MinecraftServer server = lastServer;
        return server != null && EconomyPaths.hasSharedFolder(server);
    }

    public static void reloadFromDisk(MinecraftServer server) {
        if (manager != null && lastServer == server) {
            manager.detach();
        }
        manager = null;
        lastServer = null;

        EconomyConfig.load(server);
        getManager(server);
    }

    public static void tryHandlePvpKill(ServerPlayer victim, Entity damageSource) {
        if (damageSource instanceof ServerPlayer killer) {
            getManager(victim.level().getServer()).handlePvpKill(victim, killer);
        }
    }

    public static String formatMoney(long amount) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        symbols.setGroupingSeparator(EconomyConfig.get().balanceSeparator.charAt(0));
        return "$" + new DecimalFormat("#,##0", symbols).format(amount);
    }
}
