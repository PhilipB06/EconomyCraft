package com.reazip.economycraft;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.server.MinecraftServer;

public final class EconomyCraft {
    public static final String MOD_ID = "economycraft";
    private static EconomyManager manager;
    private static MinecraftServer lastServer;

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            EconomyCommands.register(dispatcher);
        });

        LifecycleEvent.SERVER_STARTED.register(server -> {
            getManager(server); // load balances
        });
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            if (manager != null && lastServer == server) {
                manager.save();
            }
        });
    }

    public static EconomyManager getManager(MinecraftServer server) {
        if (manager == null || lastServer != server) {
            manager = new EconomyManager(server);
            lastServer = server;
        }
        return manager;
    }
}
