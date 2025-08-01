package com.economycraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class EconomyCraftFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(EconomyManager::onServerStart);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> EconomyManager.onServerStop());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            EconomyCommands.register(dispatcher);
        });
    }
}
