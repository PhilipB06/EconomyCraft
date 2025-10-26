package com.reazip.economycraft.util;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.arguments.GameProfileArgument;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public final class IdentityCompat {
    public record PlayerRef(UUID id, String name) {}
    private IdentityCompat() {}

    public static MinecraftServer serverOf(ServerPlayer p) {
        return p.level().getServer();
    }

    public static PlayerRef of(ServerPlayer p) {
        return new PlayerRef(p.getUUID(), getGameProfileName(p.getGameProfile()));
    }

    public static PlayerRef of(GameProfile gp) {
        return new PlayerRef(getGameProfileId(gp), getGameProfileName(gp));
    }

    private static UUID getGameProfileId(GameProfile gp) {
        try {
            var method = gp.getClass().getMethod("id");
            return (UUID) method.invoke(gp);
        } catch (Exception e1) {
            try {
                var method = gp.getClass().getMethod("getId");
                return (UUID) method.invoke(gp);
            } catch (Exception e2) {
                throw new IllegalStateException("Cannot access GameProfile ID", e2);
            }
        }
    }

    private static String getGameProfileName(GameProfile gp) {
        try {
            var method = gp.getClass().getMethod("name");
            return (String) method.invoke(gp);
        } catch (Exception e1) {
            try {
                var method = gp.getClass().getMethod("getName");
                return (String) method.invoke(gp);
            } catch (Exception e2) {
                throw new IllegalStateException("Cannot access GameProfile name", e2);
            }
        }
    }

    private static boolean isNameAndId(Object o) {
        return o != null && o.getClass().getName().equals("net.minecraft.server.players.NameAndId");
    }

    private static PlayerRef fromNameAndIdReflect(Object nid) {
        try {
            Method id = nid.getClass().getMethod("id");
            Method name = nid.getClass().getMethod("name");
            return new PlayerRef((UUID) id.invoke(nid), (String) name.invoke(nid));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to read NameAndId reflectively", e);
        }
    }

    public static PlayerRef fromUnknown(Object any) {
        if (any instanceof PlayerRef pr) return pr;
        if (any instanceof ServerPlayer sp) return of(sp);
        if (any instanceof GameProfile gp) return of(gp);
        if (isNameAndId(any)) return fromNameAndIdReflect(any);
        throw new IllegalArgumentException("Unsupported identity object: " + (any == null ? "null" : any.getClass()));
    }

    public static Collection<PlayerRef> getArgAsPlayerRefs(
            CommandContext<CommandSourceStack> ctx, String argName
    ) throws CommandSyntaxException {
        Collection<?> any = GameProfileArgument.getGameProfiles(ctx, argName);
        return any.stream().map(IdentityCompat::fromUnknown).collect(Collectors.toList());
    }
}
