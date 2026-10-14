package com.reazip.economycraft.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import com.reazip.economycraft.EconomyCraft;

public final class EconomyCraftFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        EconomyCraft.init();
        ServerLifecycleEvents.SERVER_STARTING.register(EconomyCraft::onServerStarting);
        CommandRegistrationCallback.EVENT.register(EconomyCraft::registerCommands);
        ServerLifecycleEvents.SERVER_STARTED.register(EconomyCraft::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(EconomyCraft::onServerStopping);
        ServerPlayerEvents.JOIN.register(EconomyCraft::onPlayerJoin);
        ServerTickEvents.END_SERVER_TICK.register(EconomyCraft::onServerTick);
        EconomyCraftFabricPermissions.install();

        if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            EconomyCraftFabricPlaceholders.register();
        }
    }
}
