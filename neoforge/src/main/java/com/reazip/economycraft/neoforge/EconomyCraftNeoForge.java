package com.reazip.economycraft.neoforge;

import net.neoforged.fml.common.Mod;
import com.reazip.economycraft.EconomyCraft;

@Mod(EconomyCraft.MOD_ID)
public final class EconomyCraftNeoForge {
    public EconomyCraftNeoForge() {
        EconomyCraft.registerEvents();
    }
}
