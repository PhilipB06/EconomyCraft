package com.reazip.economycraft.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ProfileCompat {
    private ProfileCompat() {}

    public static @Nullable String resolveCachedName(MinecraftServer server, UUID id) {
        return server.services().nameToIdCache().get(id)
                .map(NameAndId::name)
                .filter(name -> !name.isBlank())
                .orElse(null);
    }

    public static @Nullable Object fetchProfile(MinecraftServer server, UUID id) {
        return server.services().profileResolver().fetchById(id).orElse(null);
    }

    public static void cacheName(MinecraftServer server, UUID id, String name) {
        server.services().nameToIdCache().add(new NameAndId(id, name));
    }
}
