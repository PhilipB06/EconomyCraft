package com.reazip.economycraft.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

/**
 * Утилита для работы с разрешениями и правами доступа.
 */
public final class PermissionCompat {

    private PermissionCompat() {}

    /**
     * Возвращает предикат, проверяющий, имеет ли источник команды
     * права гейммастера (оператора).
     *
     * <p>Консоль, командные блоки и RCON всегда считаются имеющими права.
     * Для игроков проверяется наличие OP-статуса.</p>
     *
     * @return предикат для проверки источника команды
     */
    public static Predicate<CommandSourceStack> gamemaster() {
        return source -> {
            ServerPlayer player;
            try {
                player = source.getPlayerOrException();
            } catch (Exception e) {
                return true; // Не-игроки всегда имеют права
            }

            // Создаём GameProfile вместо NameAndId
            GameProfile profile = new GameProfile(
                    player.getUUID(),
                    player.getName().getString()
            );

            return source.getServer()
                    .getPlayerList()
                    .isOp(profile);
        };
    }

    /**
     * Возвращает источник команды с правами владельца.
     *
     * <p>В текущей реализации просто возвращает переданный источник.
     * Метод оставлен для совместимости и возможных будущих расширений.</p>
     *
     * @param source исходный источник команды
     * @return источник команды с правами владельца
     */
    public static CommandSourceStack withOwnerPermission(CommandSourceStack source) {
        return source;
    }
}