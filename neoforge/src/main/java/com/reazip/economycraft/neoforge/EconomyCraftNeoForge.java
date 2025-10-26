package com.reazip.economycraft.neoforge;

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
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        var src = event.getSource().getEntity();
        if (src instanceof ServerPlayer killer) {
            EconomyCraft.getManager(victim.level().getServer()).handlePvpKill(victim, killer);
        }
    }
}
