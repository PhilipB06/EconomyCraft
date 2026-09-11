package com.reazip.economycraft.neoforge;

import com.reazip.economycraft.EconomyCraft;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

@Mod(EconomyCraft.MOD_ID)
public final class EconomyCraftNeoForge {
    public EconomyCraftNeoForge() {
        EconomyCraft.registerEvents();

        if (EconomyCraftNeoForgeModIds.isPlaceholderApiLoaded()) {
            EconomyCraftNeoForgePlaceholders.register();
        }

        if (ModList.get().isLoaded("tab")) {
            EconomyCraftNeoForgeTab.register();
        }
    }
}
