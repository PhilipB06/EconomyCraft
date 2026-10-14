package com.reazip.economycraft.neoforge;

import com.reazip.economycraft.EconomyCraft;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class EconomyCraftNeoForgeEvents {
    private EconomyCraftNeoForgeEvents() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ServerStartingEvent.class,
                event -> EconomyCraft.onServerStarting(event.getServer()));
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RegisterCommandsEvent.class,
                event -> EconomyCraft.registerCommands(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ServerStartedEvent.class,
                event -> EconomyCraft.onServerStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ServerStoppingEvent.class,
                event -> EconomyCraft.onServerStopping(event.getServer()));
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EconomyCraft.onPlayerJoin(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ServerTickEvent.Post.class,
                event -> EconomyCraft.onServerTick(event.getServer()));
    }
}
