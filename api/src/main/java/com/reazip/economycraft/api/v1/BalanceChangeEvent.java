package com.reazip.economycraft.api.v1;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BalanceChangeEvent(
        UUID playerId,
        long previousBalance,
        long newBalance,
        BalanceMutationType type,
        Optional<UUID> counterpartyId,
        Optional<MutationSource> source
) {
    public BalanceChangeEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        counterpartyId = Objects.requireNonNull(counterpartyId, "counterpartyId");
        source = Objects.requireNonNull(source, "source");
    }

    public long difference() {
        return newBalance - previousBalance;
    }
}
