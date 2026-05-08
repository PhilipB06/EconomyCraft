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
    private static final NumberFormat FORMAT = NumberFormat.getInstance(Locale.GERMANY); // Формат чисел (немецкий)

    public static void registerEvents() {
        LifecycleEvent.SERVER_STARTING.register(EconomyConfig::load); // Загрузка конфига при запуске сервера

        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            EconomyCommands.register(dispatcher); // Регистрация команд экономики
        });

        LifecycleEvent.SERVER_STARTED.register(EconomyCraft::getManager); // Инициализация менеджера после запуска

        LifecycleEvent.SERVER_STOPPING.register(server -> {
            if (manager != null && lastServer == server) {
                manager.save(); // Сохранение данных при остановке сервера
            }
        });

        PlayerEvent.PLAYER_JOIN.register(EconomyCraft::onPlayerJoin); // Обработка входа игрока
    }

    private static void onPlayerJoin(ServerPlayer player) {
        EconomyManager eco = getManager(player.level().getServer());
        eco.getBalance(player.getUUID(), true); // Получение баланса (создание аккаунта при необходимости)

        // Проверка наличия неполученных заказов
        if (eco.getOrders().hasDeliveries(player.getUUID()) || eco.getShop().hasDeliveries(player.getUUID())) {
            ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim"); // Команда для получения заказов

            if (ev != null) {
                Component msg = Component.literal("У вас есть неполученные предметы: ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("[Получить]")
                                .withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev)));
                player.sendSystemMessage(msg);
            } else {
                ChatCompat.sendRunCommandTellraw(
                        player,
                        "У вас есть неполученные предметы: ",
                        "[Получить]",
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
        return Component.literal(baseTitle + " - Баланс: " + formatMoney(balance)); // Формирование заголовка с балансом
    }

    public static String formatMoney(long amount) {
        return "$" + FORMAT.format(amount); // Форматирование денег (доллар + число)
    }
}