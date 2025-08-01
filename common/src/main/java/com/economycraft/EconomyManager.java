package com.economycraft;

import net.minecraft.server.MinecraftServer;

/**
 * Handles loading and saving the economy data for the current server.
 * <p>
 * The common module exposes simple lifecycle hooks that loader specific
 * implementations can call when the server starts or stops. This avoids the
 * common code depending on any particular loader's event API.
 */
public class EconomyManager {
    private static EconomyStorage storage;

    /** Called by the loader when the server is starting. */
    public static void onServerStart(MinecraftServer server) {
        storage = new EconomyStorage(server);
    }

    /** Called by the loader when the server is stopping. */
    public static void onServerStop() {
        if (storage != null) {
            storage.save();
            storage = null;
        }
    }

    public static EconomyStorage get() {
        return storage;
    }
}
