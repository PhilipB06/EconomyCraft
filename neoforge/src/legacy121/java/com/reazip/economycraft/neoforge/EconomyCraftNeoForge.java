package com.reazip.economycraft.neoforge;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.client.MenuPaginationOverlay;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@Mod(EconomyCraft.MOD_ID)
public final class EconomyCraftNeoForge {
    public EconomyCraftNeoForge() {
        EconomyCraft.registerEvents();
        NeoForge.EVENT_BUS.register(this);
        EnvExecutor.runInEnv(Env.CLIENT, () -> () -> {
            MenuPaginationOverlay.register();
        });

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
            server.tell(new TickTask(server.getTickCount() + 1, () -> {
                if (!event.isCanceled()) {
                    EconomyCraft.tryHandlePvpKill(victim, damageSource);
                }
            }));
        }
    }
}
