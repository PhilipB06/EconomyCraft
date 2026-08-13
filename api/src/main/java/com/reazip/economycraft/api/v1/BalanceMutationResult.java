package com.reazip.economycraft.api.v1;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BalanceMutationResult(
        BalanceMutationStatus status,
        BalanceMutationType type,
        UUID playerId,
        long requestedAmount,
        long previousBalance,
        long newBalance,
        Optional<MutationSource> source
) {
    public BalanceMutationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(playerId, "playerId");
        source = Objects.requireNonNull(source, "source");
    }

    public boolean successful() {
        return status == BalanceMutationStatus.SUCCESS || status == BalanceMutationStatus.NO_CHANGE;
    }

    public long difference() {
        return newBalance - previousBalance;
    }
}
