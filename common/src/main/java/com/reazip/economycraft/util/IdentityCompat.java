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

public final class IdentityCompat {
    public record PlayerRef(UUID id, String name) {}
    private IdentityCompat() {}

    public static PlayerRef of(ServerPlayer p) {
        return new PlayerRef(p.getUUID(), getGameProfileName(p.getGameProfile()));
    }

    public static PlayerRef of(GameProfile gp) {
        return new PlayerRef(getGameProfileId(gp), getGameProfileName(gp));
    }

    private static UUID getGameProfileId(GameProfile gp) {
        try {
            Method m = gp.getClass().getMethod("id"); // record-style
            return (UUID) m.invoke(gp);
        } catch (Exception e1) {
            try {
                Method m = gp.getClass().getMethod("getId"); // getter-style
                return (UUID) m.invoke(gp);
            } catch (Exception e2) {
                throw new IllegalStateException("Cannot access GameProfile ID", e2);
            }
        }
    }

    private static String getGameProfileName(GameProfile gp) {
        try {
            Method m = gp.getClass().getMethod("name");
            return (String) m.invoke(gp);
        } catch (Exception e1) {
            try {
                Method m = gp.getClass().getMethod("getName");
                return (String) m.invoke(gp);
            } catch (Exception e2) {
                throw new IllegalStateException("Cannot access GameProfile name", e2);
            }
        }
    }

    private static boolean isNameAndId(Object o) {
        if (o == null) return false;
        String n = o.getClass().getName();
        return n.equals("net.minecraft.server.players.NameAndId") || n.endsWith(".NameAndId");
    }

    private static PlayerRef fromNameAndIdReflect(Object nid) {
        try {
            Method id = findNoArgMethod(nid.getClass(), "id", "getId");
            Method name = findNoArgMethod(nid.getClass(), "name", "getName");
            UUID uuid = readUuidLike(id.invoke(nid));
            String nm = readStringLike(name.invoke(nid));
            return new PlayerRef(uuid, nm);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to read NameAndId reflectively", e);
        }
    }

    public static PlayerRef fromUnknown(Object any) {
        if (any instanceof PlayerRef pr) return pr;
        if (any instanceof ServerPlayer sp) return of(sp);
        if (any instanceof GameProfile gp) return of(gp);
        if (isNameAndId(any)) return fromNameAndIdReflect(any);

        GameProfile gp = tryExtractGameProfileByType(any);
        if (gp != null) return of(gp);

        PlayerRef byIdName = tryExtractIdNameByType(any);
        if (byIdName != null) return byIdName;

        throw new IllegalArgumentException("Unsupported identity object: "
                + (any == null ? "null" : any.getClass()));
    }

    public static Collection<PlayerRef> getArgAsPlayerRefs(
            CommandContext<CommandSourceStack> ctx, String argName
    ) throws CommandSyntaxException {

        Collection<?> raw = GameProfileArgument.getGameProfiles(ctx, argName);
        return raw.stream()
                .map(IdentityCompat::fromUnknown)
                .collect(Collectors.toList());
    }

    @Nullable
    private static GameProfile tryExtractGameProfileByType(Object any) {
        if (any == null) return null;

        for (Method m : any.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;
            if (isJunkMethod(m)) continue;

            Class<?> rt = m.getReturnType();
            try {
                Object val = m.invoke(any);
                if (val instanceof GameProfile gp && GameProfile.class.isAssignableFrom(rt)) {
                    return gp;
                }
                if (val instanceof Optional<?> opt) {
                    Object inner = opt.orElse(null);
                    if (inner instanceof GameProfile gp2) return gp2;
                }

            } catch (Exception ignored) {}
        }
        return null;
    }

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

        if (id == null && name != null) {
            id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        }

        if (id == null) return null;
        return new PlayerRef(id, name);
    }

    private static boolean isJunkMethod(Method m) {
        String n = m.getName();
        return n.equals("getClass") || n.equals("hashCode") || n.equals("toString") || n.equals("equals");
    }

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

    private static Method findNoArgMethod(Class<?> cls, String... names) throws NoSuchMethodException {
        for (String n : names) {
            try {
                return cls.getMethod(n);
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(Arrays.toString(names));
    }

    @Nullable
    private static UUID readUuidLike(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof UUID u) {
            return u;
        }
        if (val instanceof Optional<?> opt) {
            Object inner = opt.orElse(null);
            return readUuidLike(inner);
        }
        if (val instanceof GameProfile gp) {
            return getGameProfileId(gp);
        }
        return null;
    }

    @Nullable
    private static String readStringLike(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof String s) {
            return s;
        }
        if (val instanceof Optional<?> opt) {
            Object inner = opt.orElse(null);
            return readStringLike(inner);
        }
        return String.valueOf(val);
    }
}
