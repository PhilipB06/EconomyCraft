package com.reazip.economycraft.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ProfileCompat {
    private ProfileCompat() {}

    public static @Nullable String resolveCachedName(MinecraftServer server, UUID id) {
        return server.getProfileCache().get(id)
                .map(IdentityCompat::of)
                .map(IdentityCompat.PlayerRef::name)
                .filter(name -> !name.isBlank())
                .orElse(null);
    }

    public static @Nullable Object fetchProfile(MinecraftServer server, UUID id) {
        ProfileResult result = server.getSessionService().fetchProfile(id, false);
        return result == null ? null : result.profile();
    }

    public static void cacheName(MinecraftServer server, UUID id, String name) {
        server.getProfileCache().add(new GameProfile(id, name));
    }
}
