package com.reazip.economycraft.neoforge;

import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.client.ShopSearchOverlay;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

@Mod(EconomyCraft.MOD_ID)
public final class EconomyCraftNeoForge {
    public EconomyCraftNeoForge() {
        EconomyCraft.registerEvents();
        NeoForge.EVENT_BUS.register(this);
        EnvExecutor.runInEnv(Env.CLIENT, () -> ShopSearchOverlay::register);

        if (EconomyCraftNeoForgeModIds.isPlaceholderApiLoaded()) {
            EconomyCraftNeoForgePlaceholders.register();
        }

        if (ModList.get().isLoaded("tab")) {
            EconomyCraftNeoForgeTab.register();
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim) {
            MinecraftServer server = victim.level().getServer();
            Entity damageSource = event.getSource().getEntity();
            server.schedule(new TickTask(server.getTickCount() + 1, () -> {
                if (!event.isCanceled()) {
                    EconomyCraft.tryHandlePvpKill(victim, damageSource);
                }
            }));
        }
    }
}
