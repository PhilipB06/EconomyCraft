package com.reazip.economycraft.fabric;

import net.fabricmc.api.ModInitializer;
import com.reazip.economycraft.EconomyCraft;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

public final class EconomyCraftFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        EconomyCraft.registerEvents();

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayer victim)) return;

            var src = damageSource.getEntity();
            if (src instanceof ServerPlayer killer) {
                EconomyCraft.getManager(victim.level().getServer()).handlePvpKill(victim, killer);
            }
        });
    }
}
