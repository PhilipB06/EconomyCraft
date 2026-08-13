package com.reazip.economycraft.api.v1;

import net.minecraft.server.MinecraftServer;

import java.util.Objects;

final class EconomyCraftApiAccess {
    private static volatile Provider provider;

    private EconomyCraftApiAccess() {}

    static EconomyCraftApi get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        Provider current = provider;
        if (current == null) {
            throw new IllegalStateException("EconomyCraft API is not initialized");
        }
        return Objects.requireNonNull(current.get(server), "EconomyCraft API provider returned null");
    }

    static synchronized void install(Provider newProvider) {
        Objects.requireNonNull(newProvider, "newProvider");
        if (provider != null && provider != newProvider) {
            throw new IllegalStateException("EconomyCraft API provider is already installed");
        }
        provider = newProvider;
    }

    @FunctionalInterface
    interface Provider {
        EconomyCraftApi get(MinecraftServer server);
    }
}
