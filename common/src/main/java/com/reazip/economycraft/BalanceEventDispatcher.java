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
import java.util.function.BiConsumer;
import net.minecraft.server.MinecraftServer;

final class BalanceEventDispatcher implements BalanceEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, BalanceEventDispatcher> BY_SERVER = new IdentityHashMap<>();
    private final CopyOnWriteArrayList<BalanceChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<BiConsumer<BalanceChangeEvent, String>> internalListeners = new CopyOnWriteArrayList<>();

    static synchronized BalanceEventDispatcher forServer(MinecraftServer server) {
        return BY_SERVER.computeIfAbsent(server, ignored -> new BalanceEventDispatcher());
    }

    static synchronized void release(MinecraftServer server) {
        BalanceEventDispatcher dispatcher = BY_SERVER.remove(server);
        if (dispatcher != null) {
            dispatcher.listeners.clear();
            dispatcher.internalListeners.clear();
        }
    }

    void registerInternal(BiConsumer<BalanceChangeEvent, String> listener) {
        internalListeners.add(Objects.requireNonNull(listener, "listener"));
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
        emit(event, null);
    }

    void emit(BalanceChangeEvent event, String item) {
        for (BiConsumer<BalanceChangeEvent, String> listener : internalListeners) {
            try {
                listener.accept(event, item);
            } catch (Throwable error) {
                LOGGER.error("[EconomyCraft] Internal balance change listener failed", error);
            }
        }
        for (BalanceChangeListener listener : listeners) {
            try {
                listener.onBalanceChanged(event);
            } catch (Throwable error) {
                LOGGER.error("[EconomyCraft] Balance change listener failed", error);
            }
        }
    }
}
