package com.reazip.economycraft.neoforge;

import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import com.reazip.economycraft.EconomyCraft;

@Mod(EconomyCraft.MOD_ID)
public final class EconomyCraftNeoForge {
    public EconomyCraftNeoForge() {
        EconomyCraft.registerEvents();
        EconomyCraftNeoForgePermissions.bootstrap();

        if (EconomyCraftNeoForgeModIds.isPlaceholderApiLoaded()) {
            EconomyCraftNeoForgePlaceholders.register();
        }

        if (ModList.get().isLoaded("tab")) {
            EconomyCraftNeoForgeTab.register();
        }
    }
}
