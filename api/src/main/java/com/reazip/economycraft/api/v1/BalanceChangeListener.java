package com.reazip.economycraft.api.v1;

@FunctionalInterface
public interface BalanceChangeListener {
    void onBalanceChanged(BalanceChangeEvent event);
}
