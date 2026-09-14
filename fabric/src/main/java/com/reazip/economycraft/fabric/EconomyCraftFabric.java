package com.reazip.economycraft.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import com.reazip.economycraft.EconomyCraft;

public final class EconomyCraftFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        EconomyCraft.registerEvents();
        EconomyCraftFabricPermissions.install();

        if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            EconomyCraftFabricPlaceholders.register();
        }
    }
}
