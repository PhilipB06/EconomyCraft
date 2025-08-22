package com.reazip.economycraft.fabric;

import net.fabricmc.api.ModInitializer;
import com.reazip.economycraft.EconomyCraft;

public final class EconomyCraftFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        EconomyCraft.registerEvents();
    }
}
