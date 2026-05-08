package com.reazip.economycraft.util;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Утилита для унифицированного получения данных об игроке (UUID и имя)
 * из различных типов объектов (ServerPlayer, GameProfile, NameAndId и т.д.)
 * с использованием рефлексии для совместимости с разными версиями.
 */
public final class IdentityCompat {
    /**
     * Запись, содержащая UUID и имя игрока.
     */
    public record PlayerRef(UUID id, String name) {}
    
    private IdentityCompat() {}

    /**
     * Создаёт PlayerRef из ServerPlayer.
     */
    public static PlayerRef of(ServerPlayer p) {
        return new PlayerRef(p.getUUID(), getGameProfileName(p.getGameProfile()));
    }

    /**
     * Создаёт PlayerRef из GameProfile.
     */
    public static PlayerRef of(GameProfile gp) {
        return new PlayerRef(getGameProfileId(gp), getGameProfileName(gp));
    }

    /**
     * Получает UUID из GameProfile через рефлексию (поддержка разных маппингов).
     */
    private static UUID getGameProfileId(GameProfile gp) {
        try {
            Method m = gp.getClass().getMethod("id"); // record-стиль
            return (UUID) m.invoke(gp);
        } catch (Exception e1) {
            try {
                Method m = gp.getClass().getMethod("getId"); // getter-стиль
                return (UUID) m.invoke(gp);
            } catch (Exception e2) {
                throw new IllegalStateException("Не удаётся получить доступ к ID GameProfile", e2);
            }
        }
    }

    /**
     * Получает имя из GameProfile через рефлексию (поддержка разных маппингов).
     */
    private static String getGameProfileName(GameProfile gp) {
        try {
            Method m = gp.getClass().getMethod("name");
            return (String) m.invoke(gp);
        } catch (Exception e1) {
            try {
                Method m = gp.getClass().getMethod("getName");
                return (String) m.invoke(gp);
            } catch (Exception e2) {
                throw new IllegalStateException("Не удаётся получить доступ к имени GameProfile", e2);
            }
        }
    }

    /**
     * Проверяет, является ли объект внутренним классом NameAndId.
     */
    private static boolean isNameAndId(Object o) {
        if (o == null) return false;
        String n = o.getClass().getName();
        return n.equals("net.minecraft.server.players.NameAndId") || n.endsWith(".NameAndId");
    }

    /**
     * Извлекает PlayerRef из объекта NameAndId через рефлексию.
     */
    private static PlayerRef fromNameAndIdReflect(Object nid) {
        try {
            Method id = findNoArgMethod(nid.getClass(), "id", "getId");
            Method name = findNoArgMethod(nid.getClass(), "name", "getName");
            UUID uuid = readUuidLike(id.invoke(nid));
            String nm = readStringLike(name.invoke(nid));
            return new PlayerRef(uuid, nm);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Не удалось прочитать NameAndId через рефлексию", e);
        }
    }

    /**
     * Преобразует любой поддерживаемый объект в PlayerRef.
     * @param any объект (ServerPlayer, GameProfile, NameAndId и т.д.)
     * @return PlayerRef с UUID и именем игрока
     * @throws IllegalArgumentException если тип объекта не поддерживается
     */
    public static PlayerRef fromUnknown(Object any) {
        if (any instanceof PlayerRef pr) return pr;
        if (any instanceof ServerPlayer sp) return of(sp);
        if (any instanceof GameProfile gp) return of(gp);
        if (isNameAndId(any)) return fromNameAndIdReflect(any);

        GameProfile gp = tryExtractGameProfileByType(any);
        if (gp != null) return of(gp);

        PlayerRef byIdName = tryExtractIdNameByType(any);
        if (byIdName != null) return byIdName;

        throw new IllegalArgumentException("Неподдерживаемый тип объекта идентификации: "
                + (any == null ? "null" : any.getClass()));
    }

    /**
     * Получает список PlayerRef из аргумента команды типа GameProfileArgument.
     */
    public static Collection<PlayerRef> getArgAsPlayerRefs(
            CommandContext<CommandSourceStack> ctx, String argName
    ) throws CommandSyntaxException {

        Collection<?> raw = GameProfileArgument.getGameProfiles(ctx, argName);
        return raw.stream()
                .map(IdentityCompat::fromUnknown)
                .collect(Collectors.toList());
    }

    /**
     * Пытается извлечь GameProfile из произвольного объекта через рефлексию.
     */
    @Nullable
    private static GameProfile tryExtractGameProfileByType(Object any) {
        if (any == null) return null;

        for (Method m : any.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;
            if (isJunkMethod(m)) continue;

            Class<?> rt = m.getReturnType();
            try {
                Object val = m.invoke(any);
                switch (val) {
                    case GameProfile gp when GameProfile.class.isAssignableFrom(rt) -> {
                        return gp;
                    }
                    case Optional<?> opt -> {
                        Object inner = opt.orElse(null);
                        if (inner instanceof GameProfile gp2) return gp2;
                    }
                    case null, default -> {
                    }
                }

            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Пытается извлечь UUID и имя из произвольного объекта через рефлексию.
     */
    @Nullable
    private static PlayerRef tryExtractIdNameByType(Object any) {
        if (any == null) return null;

        UUID id;
        String name;

        id = invokeUuidIfExists(any, "id", "getId", "uuid", "getUuid");
        name = invokeStringIfExists(any, "name", "getName");

        for (Method m : any.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;
            if (isJunkMethod(m)) continue;

            try {
                Object val = m.invoke(any);
                if (val == null) continue;

                if (id == null) {
                    UUID maybe = readUuidLike(val);
                    if (maybe != null) id = maybe;
                }

                if (name == null && val instanceof String s && !s.isBlank()) {
                    name = s;
                }
            } catch (Exception ignored) {}
        }

        // Генерируем оффлайн UUID на основе имени, если есть только имя
        if (id == null && name != null) {
            id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        }

        if (id == null) return null;
        return new PlayerRef(id, name);
    }

    /**
     * Проверяет, является ли метод "мусорным" (не содержит полезных данных).
     */
    private static boolean isJunkMethod(Method m) {
        String n = m.getName();
        return n.equals("getClass") || n.equals("hashCode") || n.equals("toString") || n.equals("equals");
    }

    /**
     * Вызывает указанные методы и пытается получить UUID из результата.
     */
    @Nullable
    private static UUID invokeUuidIfExists(Object any, String... names) {
        for (String n : names) {
            try {
                Method m = any.getClass().getMethod(n);
                Object val = m.invoke(any);
                UUID u = readUuidLike(val);
                if (u != null) return u;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Вызывает указанные методы и пытается получить строку из результата.
     */
    @Nullable
    private static String invokeStringIfExists(Object any, String... names) {
        for (String n : names) {
            try {
                Method m = any.getClass().getMethod(n);
                Object val = m.invoke(any);
                String s = readStringLike(val);
                if (s != null && !s.isBlank()) return s;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Ищет метод без аргументов по одному из возможных имён.
     */
    private static Method findNoArgMethod(Class<?> cls, String... names) throws NoSuchMethodException {
        for (String n : names) {
            try {
                return cls.getMethod(n);
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(Arrays.toString(names));
    }

    /**
     * Преобразует различные типы объектов в UUID (поддержка UUID, Optional, GameProfile).
     */
    @Nullable
    private static UUID readUuidLike(Object val) {
        switch (val) {
            case null -> {
                return null;
            }
            case UUID u -> {
                return u;
            }
            case Optional<?> opt -> {
                Object inner = opt.orElse(null);
                return readUuidLike(inner);
            }
            case GameProfile gp -> {
                return getGameProfileId(gp);
            }
            default -> {
            }
        }
        return null;
    }

    /**
     * Преобразует различные типы объектов в строку.
     */
    @Nullable
    private static String readStringLike(Object val) {
        switch (val) {
            case null -> {
                return null;
            }
            case String s -> {
                return s;
            }
            case Optional<?> opt -> {
                Object inner = opt.orElse(null);
                return readStringLike(inner);
            }
            default -> {
            }
        }
        return String.valueOf(val);
    }
}