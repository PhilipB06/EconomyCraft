package com.reazip.economycraft;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.reazip.economycraft.orders.OrderManager;
import com.reazip.economycraft.shop.ShopManager;
import com.reazip.economycraft.util.IdentityCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.time.LocalDate;

public class EconomyManager {
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<UUID, Long>>(){}.getType();
    private static final Type DAILY_SELL_TYPE = new TypeToken<Map<UUID, DailySellData>>(){}.getType();
    private static final String ECO_BALANCE_OBJECTIVE = "eco_balance";
    private static final int LEADERBOARD_SIZE = 5;

    private final MinecraftServer server;
    private final Path file;
    private final Path dailyFile;
    private final Path dailySellFile;

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, Long> lastDaily = new HashMap<>();
    private final Map<UUID, DailySellData> dailySells = new HashMap<>();
    private Map<UUID, String> diskUserCache = null;
    private final PriceRegistry prices;

    private Objective objective;
    private final DeliveryManager deliveries;
    private final ShopManager shop;
    private final OrderManager orders;
    private final Map<UUID, String> displayed = new HashMap<>();

    public static final long MAX = 999_999_999L;

    public EconomyManager(MinecraftServer server) {
        this.server = server;
        Path dir = server.getFile("config/economycraft");
        Path dataDir = dir.resolve("data");
        try { Files.createDirectories(dataDir); } catch (IOException ignored) {}

        this.file = dataDir.resolve("balances.json");
        this.dailyFile = dataDir.resolve("daily.json");
        this.dailySellFile = dataDir.resolve("daily_sells.json");

        load();
        loadDaily();
        loadDailySells();

        this.deliveries = new DeliveryManager(server);
        this.shop = new ShopManager(server, deliveries);
        this.orders = new OrderManager(server, deliveries);

        applyScoreboardSettingOnStartup();
        this.prices = new PriceRegistry(server);
    }

    public MinecraftServer getServer() {
        return server;
    }

    // ---- Name handling ----

    private void ensureDiskUserCacheLoaded() {
        if (diskUserCache != null) return;
        diskUserCache = new HashMap<>();
        try {
            Path uc = server.getFile("usercache.json");
            if (!Files.exists(uc)) return;

            String json = Files.readString(uc);
            UserCacheEntry[] entries = GSON.fromJson(json, UserCacheEntry[].class);
            if (entries == null) return;

            for (UserCacheEntry e : entries) {
                if (e == null || e.uuid == null || e.uuid.isBlank() || e.name == null || e.name.isBlank()) continue;
                try {
                    diskUserCache.put(UUID.fromString(e.uuid), e.name);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private String resolveName(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return IdentityCompat.of(online).name();
        ensureDiskUserCacheLoaded();
        String fromDisk = diskUserCache.get(id);
        if (fromDisk != null && !fromDisk.isBlank()) return fromDisk;
        return id.toString();
    }

    public @Nullable String getBestName(UUID id) {
        return resolveName(server, id);
    }

    public UUID tryResolveUuidByName(String name) {
        if (name == null || name.isBlank()) return null;

        // 1) Online
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();

        // 2) Offline from usercache.json
        ensureDiskUserCacheLoaded();
        for (var e : diskUserCache.entrySet()) {
            if (name.equalsIgnoreCase(e.getValue())) return e.getKey();
        }

        // 3) direct UUID string
        try { return UUID.fromString(name); } catch (IllegalArgumentException ignored) {}

        return null;
    }

    // ---- Balances ----

    public Long getBalance(UUID player, boolean newBalanceIfNonExistent) {
        if (!balances.containsKey(player)) {
            if (newBalanceIfNonExistent) {
                long balance = clamp(EconomyConfig.get().startingBalance);
                balances.put(player, balance);
                updateLeaderboard();
                return balance;
            } else {
                return null;
            }
        }
        return balances.get(player);
    }

    public void addMoney(UUID player, long amount) {
        balances.put(player, clamp(getBalance(player, true) + amount));
        updateLeaderboard();
        save();
    }

    public void setMoney(UUID player, long amount) {
        balances.put(player, clamp(amount));
        updateLeaderboard();
        save();
    }

    public boolean removeMoney(UUID player, long amount) {
        if (amount < 0) return false;
        long balance = getBalance(player, true);
        if (balance < amount) return false;
        balances.put(player, clamp(balance - amount));
        updateLeaderboard();
        save();
        return true;
    }

    public boolean pay(UUID from, UUID to, long amount) {
        Long balance = getBalance(from, false);
        if (balance == null || balance < amount) return false;
        if (!removeMoney(from, amount)) return false;
        addMoney(to, amount);
        return true;
    }

    // ---- Load / Save ----

    public void load() {
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                Map<UUID, Double> map = GSON.fromJson(json, new TypeToken<Map<UUID, Double>>(){}.getType());
                if (map != null) {
                    for (Map.Entry<UUID, Double> e : map.entrySet()) {
                        if (e.getValue() == null) continue;
                        balances.put(e.getKey(), clamp(e.getValue().longValue()));
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    public void save() {
        try {
            Map<UUID, Long> data = new HashMap<>(balances);
            String json = GSON.toJson(data, TYPE);
            Files.writeString(file, json);
        } catch (IOException ignored) {}

        try {
            String json = GSON.toJson(lastDaily, new TypeToken<Map<UUID, Long>>(){}.getType());
            Files.writeString(dailyFile, json);
        } catch (IOException ignored) {}

        try {
            String json = GSON.toJson(dailySells, DAILY_SELL_TYPE);
            Files.writeString(dailySellFile, json);
        } catch (IOException ignored) {}
    }

    private void loadDaily() {
        if (Files.exists(dailyFile)) {
            try {
                String json = Files.readString(dailyFile);
                Map<UUID, Long> map = GSON.fromJson(json, new TypeToken<Map<UUID, Long>>(){}.getType());
                if (map != null) {
                    for (Map.Entry<UUID, Long> e : map.entrySet()) {
                        if (e.getValue() != null) lastDaily.put(e.getKey(), e.getValue());
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    private void loadDailySells() {
        if (Files.exists(dailySellFile)) {
            try {
                String json = Files.readString(dailySellFile);
                Map<UUID, DailySellData> map = GSON.fromJson(json, DAILY_SELL_TYPE);
                if (map != null) dailySells.putAll(map);
            } catch (IOException ignored) {}
        }
    }

    // ---- Scoreboard / Leaderboard ----

    private void applyScoreboardSettingOnStartup() {
        if (EconomyConfig.get().scoreboardEnabled) {
            setupObjective();
        } else {
            teardownObjective(server.getScoreboard());
        }
    }

    private Objective createBalanceObjective(Scoreboard board) {
        return board.addObjective(
                ECO_BALANCE_OBJECTIVE,
                ObjectiveCriteria.DUMMY,
                Component.literal("Balance"),
                ObjectiveCriteria.RenderType.INTEGER,
                true,
                null
        );
    }

    /** Claims the SIDEBAR slot only if we don't already hold it, so other plugins/mods keeping the slot afterward aren't fought over on every score update. */
    private void ensureObjective(Scoreboard board) {
        if (objective != null) return;

        objective = board.getObjective(ECO_BALANCE_OBJECTIVE);
        if (objective == null) {
            objective = createBalanceObjective(board);
        }
        board.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
    }

    /** Releases the SIDEBAR slot. A no-op if we don't currently hold an objective, so callers on a hot path (every balance change) can skip touching the scoreboard entirely. */
    private void teardownObjective(Scoreboard board) {
        Objective existing = objective != null ? objective : board.getObjective(ECO_BALANCE_OBJECTIVE);
        if (existing == null) return;

        board.setDisplayObjective(DisplaySlot.SIDEBAR, null);
        board.removeObjective(existing);
        objective = null;
        displayed.clear();
    }

    private void setupObjective() {
        ensureObjective(server.getScoreboard());
        updateLeaderboard();
    }

    private void updateLeaderboard() {
        Scoreboard board = server.getScoreboard();

        if (!EconomyConfig.get().scoreboardEnabled) {
            teardownObjective(board);
            return;
        }

        ensureObjective(board);

        Map<UUID, String> updated = new HashMap<>();
        for (LeaderboardEntry e : computeLeaderboard(LEADERBOARD_SIZE)) {
            board.getOrCreatePlayerScore(
                    ScoreHolder.forNameOnly(e.name()),
                    objective
            ).set((int) e.balance());
            updated.put(e.id(), e.name());
        }

        for (var e : displayed.entrySet()) {
            if (!updated.containsKey(e.getKey())) {
                board.resetSinglePlayerScore(ScoreHolder.forNameOnly(e.getValue()), objective);
            }
        }
        displayed.clear();
        displayed.putAll(updated);
    }

    private List<LeaderboardEntry> computeLeaderboard(int limit) {
        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(balances.entrySet());
        sorted.sort((a, b) -> {
            int c = Long.compare(b.getValue(), a.getValue());
            if (c != 0) return c;

            String an = resolveName(server, a.getKey());
            String bn = resolveName(server, b.getKey());
            c = String.CASE_INSENSITIVE_ORDER.compare(an, bn);
            if (c != 0) return c;

            return a.getKey().compareTo(b.getKey());
        });

        List<LeaderboardEntry> result = new ArrayList<>();
        for (var e : sorted.stream().limit(limit).toList()) {
            result.add(new LeaderboardEntry(e.getKey(), resolveName(server, e.getKey()), e.getValue()));
        }
        return result;
    }

    /** Returns the Nth-highest balance (1-based rank), or null if fewer than {@code rank} players exist. */
    public @Nullable LeaderboardEntry getLeaderboardEntry(int rank) {
        if (rank < 1) return null;
        List<LeaderboardEntry> top = computeLeaderboard(rank);
        return top.size() < rank ? null : top.get(rank - 1);
    }

    public record LeaderboardEntry(UUID id, String name, long balance) {}

    public boolean toggleScoreboard() {
        EconomyConfig.get().scoreboardEnabled = !EconomyConfig.get().scoreboardEnabled;
        EconomyConfig.save();

        if (EconomyConfig.get().scoreboardEnabled) {
            setupObjective();
        } else {
            teardownObjective(server.getScoreboard());
        }

        return EconomyConfig.get().scoreboardEnabled;
    }

    // ---- Misc ----

    public ShopManager getShop() {
        return shop;
    }

    public OrderManager getOrders() {
        return orders;
    }

    public DeliveryManager getDeliveries() {
        return deliveries;
    }

    public PriceRegistry getPrices() {
        return prices;
    }

    public Map<UUID, Long> getBalances() {
        return balances;
    }

    public void removePlayer(UUID id) {
        balances.remove(id);
        updateLeaderboard();
        save();
    }

    public boolean claimDaily(UUID player) {
        long today = LocalDate.now().toEpochDay();
        long last = lastDaily.getOrDefault(player, -1L);
        if (last == today) return false;
        lastDaily.put(player, today);
        addMoney(player, EconomyConfig.get().dailyAmount);
        return true;
    }

    public boolean tryRecordDailySell(UUID player, long saleAmount) {
        long limit = EconomyConfig.get().dailySellLimit;
        if (limit <= 0) return false;

        DailySellData data = getOrCreateTodaySellData(player);
        long newTotal = data.amount() + saleAmount;
        if (newTotal > limit) {
            return true;
        }

        dailySells.put(player, new DailySellData(data.day(), newTotal));
        return false;
    }

    public long getDailySellRemaining(UUID player) {
        long limit = EconomyConfig.get().dailySellLimit;
        if (limit <= 0) return Long.MAX_VALUE;

        DailySellData data = getOrCreateTodaySellData(player);
        return Math.max(0, limit - data.amount());
    }

    private DailySellData getOrCreateTodaySellData(UUID player) {
        long today = LocalDate.now().toEpochDay();
        DailySellData data = dailySells.get(player);
        if (data == null || data.day() != today) {
            data = new DailySellData(today, 0L);
            dailySells.put(player, data);
        }
        return data;
    }

    public void handlePvpKill(ServerPlayer victim, ServerPlayer killer) {
        double pct = Math.min(EconomyConfig.get().pvpBalanceLossPercentage, 1.0);
        if (pct <= 0.0) return;
        if (victim == null || killer == null) return;
        if (victim.getUUID().equals(killer.getUUID())) return;

        long victimBal = getBalance(victim.getUUID(), true);
        if (victimBal <= 0L) return;

        long loss = Math.min((long)Math.floor(pct * victimBal), victimBal);
        if (loss <= 0L) return;
        if (!removeMoney(victim.getUUID(), loss)) return;

        addMoney(killer.getUUID(), loss);

        victim.sendSystemMessage(Component.literal(
                "You lost " + EconomyCraft.formatMoney(loss) + " for being killed by " + killer.getName().getString())
                .withStyle(ChatFormatting.RED));

        killer.sendSystemMessage(Component.literal(
                "You received " + EconomyCraft.formatMoney(loss) + " for killing " + victim.getName().getString())
                .withStyle(ChatFormatting.GREEN));
    }

    private long clamp(long value) {
        return Math.clamp(value, 0, MAX);
    }

    private static final class UserCacheEntry {
        String name;
        String uuid;
    }

    private record DailySellData(long day, long amount) {}
}
