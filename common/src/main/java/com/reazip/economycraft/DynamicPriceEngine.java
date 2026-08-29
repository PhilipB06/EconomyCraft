package com.reazip.economycraft;

import com.mojang.logging.LogUtils;
import com.reazip.economycraft.util.UuidLongMapStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DynamicPriceEngine {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long REFRESH_INTERVAL_MS = TimeUnit.HOURS.toMillis(1);

    private final Path file;
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private volatile double multiplier = 1.0;
    private volatile long lastRefreshMs = 0L;

    public DynamicPriceEngine(Path dataDir) {
        this.file = dataDir.resolve("player_activity.json");
        UuidLongMapStore.load(file, lastActive);
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void markActive(UUID player) {
        if (player == null) return;
        lastActive.put(player, System.currentTimeMillis());
        dirty.set(true);
    }

    public void flush() {
        if (dirty.compareAndSet(true, false)) UuidLongMapStore.persist(file, lastActive);
    }

    public void maybeRefresh(MinecraftServer server, Map<UUID, Long> balances) {
        flush();
        if (System.currentTimeMillis() - lastRefreshMs < REFRESH_INTERVAL_MS) return;
        refresh(server, balances);
    }

    public void refresh(MinecraftServer server, Map<UUID, Long> balances) {
        lastRefreshMs = System.currentTimeMillis();
        markActiveBatch(onlinePlayerIds(server));
        flush();

        EconomyConfig config = EconomyConfig.get();
        long reference = config.startingBalance;
        if (reference <= 0) {
            multiplier = 1.0;
            LOGGER.warn("[EconomyCraft] Dynamic prices: startingBalance is {} (must be positive to compute a reference median); leaving the multiplier at 1x.", reference);
            return;
        }

        List<Long> activeBalances = collectActiveBalances(balances, config.dynamicPriceMinActiveDays);
        if (activeBalances.isEmpty()) {
            multiplier = 1.0;
            return;
        }

        double median = median(activeBalances);
        double raw = median / (double) reference;
        multiplier = Math.clamp(raw, config.dynamicPriceMinMultiplier, config.dynamicPriceMaxMultiplier);
        LOGGER.info("[EconomyCraft] Dynamic prices: {} active player(s), median balance {}, reference {}, multiplier {}x",
                activeBalances.size(), median, reference, multiplier);
    }

    public long applyMultiplier(long baseBuyPrice) {
        if (baseBuyPrice <= 0) return baseBuyPrice;
        long rounded = Math.round(baseBuyPrice * multiplier);
        return Math.clamp(rounded, 1, EconomyManager.MAX);
    }

    private static List<UUID> onlinePlayerIds(MinecraftServer server) {
        List<UUID> online = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
        }
        return online;
    }

    private void markActiveBatch(Collection<UUID> players) {
        if (players.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (UUID id : players) lastActive.put(id, now);
        dirty.set(true);
    }

    private List<Long> collectActiveBalances(Map<UUID, Long> balances, int minActiveDays) {
        List<Long> out = new ArrayList<>(balances.size());
        long cutoff = minActiveDays > 0
                ? System.currentTimeMillis() - TimeUnit.DAYS.toMillis(minActiveDays)
                : Long.MIN_VALUE;

        for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
            if (entry.getValue() == null) continue;
            if (minActiveDays > 0) {
                Long seen = lastActive.get(entry.getKey());
                if (seen == null || seen < cutoff) continue;
            }
            out.add(entry.getValue());
        }
        return out;
    }

    private static double median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
