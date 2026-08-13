package com.reazip.economycraft.api.v1;

import java.util.Objects;
import java.util.UUID;

public record LeaderboardEntry(UUID playerId, long balance) {
    public LeaderboardEntry {
        Objects.requireNonNull(playerId, "playerId");
    }
}
