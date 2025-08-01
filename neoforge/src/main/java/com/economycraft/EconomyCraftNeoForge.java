package com.economycraft;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.server.FMLServerStartingEvent;
import net.neoforged.fml.event.server.FMLServerStoppingEvent;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
import net.neoforged.neoforge.server.command.CommandRegistryEvent;

@Mod(EconomyCraft.MOD_ID)
public class EconomyCraftNeoForge {
    public EconomyCraftNeoForge() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerCommands);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::serverStarting);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::serverStopping);
    }

    private void setup(final FMLCommonSetupEvent event) {
    }

    private void serverStarting(final FMLServerStartingEvent event) {
        EconomyManager.onServerStart(event.getServer());
    }

    private void serverStopping(final FMLServerStoppingEvent event) {
        EconomyManager.onServerStop();
    }

    private void registerCommands(CommandRegistryEvent event) {
        EconomyCommands.register(event.getDispatcher());
    }
}
