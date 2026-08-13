package com.reazip.economycraft.api.v1;

public interface ListenerRegistration extends AutoCloseable {
    void unregister();

    @Override
    default void close() {
        unregister();
    }
}
