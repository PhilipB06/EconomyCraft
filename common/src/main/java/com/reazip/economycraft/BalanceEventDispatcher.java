package com.reazip.economycraft;

import com.mojang.logging.LogUtils;
import com.reazip.economycraft.api.v1.BalanceChangeEvent;
import com.reazip.economycraft.api.v1.BalanceChangeListener;
import com.reazip.economycraft.api.v1.BalanceEvents;
import com.reazip.economycraft.api.v1.ListenerRegistration;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;

final class BalanceEventDispatcher implements BalanceEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, BalanceEventDispatcher> BY_SERVER = new IdentityHashMap<>();
    private final CopyOnWriteArrayList<BalanceChangeListener> listeners = new CopyOnWriteArrayList<>();

    static synchronized BalanceEventDispatcher forServer(MinecraftServer server) {
        return BY_SERVER.computeIfAbsent(server, ignored -> new BalanceEventDispatcher());
    }

    static synchronized void release(MinecraftServer server) {
        BalanceEventDispatcher dispatcher = BY_SERVER.remove(server);
        if (dispatcher != null) dispatcher.listeners.clear();
    }

    @Override
    public ListenerRegistration register(BalanceChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        AtomicBoolean registered = new AtomicBoolean(true);
        return () -> {
            if (registered.compareAndSet(true, false)) {
                listeners.remove(listener);
            }
        };
    }

    void emit(BalanceChangeEvent event) {
        for (BalanceChangeListener listener : listeners) {
            try {
                listener.onBalanceChanged(event);
            } catch (Throwable error) {
                LOGGER.error("[EconomyCraft] Balance change listener failed", error);
            }
        }
    }
}
