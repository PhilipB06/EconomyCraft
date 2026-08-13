package com.reazip.economycraft.api.v1;

public interface BalanceEvents {
    ListenerRegistration register(BalanceChangeListener listener);
}
