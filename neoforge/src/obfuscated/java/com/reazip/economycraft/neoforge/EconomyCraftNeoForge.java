package com.reazip.economycraft.neoforge;

import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import com.reazip.economycraft.EconomyCraft;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.minecraft.server.level.ServerPlayer;

@Mod(EconomyCraft.MOD_ID)
public final class EconomyCraftNeoForge {
    public EconomyCraftNeoForge() {
        EconomyCraft.registerEvents();
        NeoForge.EVENT_BUS.register(this);

        if (ModList.get().isLoaded("placeholder_api_neoforge") || ModList.get().isLoaded("placeholder-api-neoforge")) {
            EconomyCraftNeoForgePlaceholders.register();
        }

        if (ModList.get().isLoaded("tab")) {
            EconomyCraftNeoForgeTab.register();
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim) {
            EconomyCraft.tryHandlePvpKill(victim, event.getSource().getEntity());
        }
    }
}
