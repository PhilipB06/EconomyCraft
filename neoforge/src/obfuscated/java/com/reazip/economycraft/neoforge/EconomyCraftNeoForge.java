package com.reazip.economycraft.neoforge;

import net.neoforged.fml.common.Mod;
import com.reazip.economycraft.EconomyCraft;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.minecraft.server.level.ServerPlayer;

// No placeholder-api-neoforge wiring here: that library was never published for the 1.21.x line
// (only 26.x+), so there's nothing to hook into on this platform/version combination.
@Mod(EconomyCraft.MOD_ID)
public final class EconomyCraftNeoForge {
    public EconomyCraftNeoForge() {
        EconomyCraft.registerEvents();
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim) {
            EconomyCraft.tryHandlePvpKill(victim, event.getSource().getEntity());
        }
    }
}
