package com.reazip.economycraft.api.v1;

import java.util.List;
import java.util.Optional;

public interface LeaderboardApi {
    List<LeaderboardEntry> getLeaderboardEntries(int limit);

    Optional<LeaderboardEntry> getLeaderboardEntry(int rank);
}
