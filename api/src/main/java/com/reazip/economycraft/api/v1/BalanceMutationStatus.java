package com.reazip.economycraft.api.v1;

public enum BalanceMutationStatus {
    SUCCESS,
    NO_CHANGE,
    INVALID_AMOUNT,
    INSUFFICIENT_FUNDS,
    SAME_PLAYER,
    MAX_BALANCE_EXCEEDED
}
