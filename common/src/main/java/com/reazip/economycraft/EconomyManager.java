package com.reazip.economycraft;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.reazip.economycraft.util.IdentityCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.Team;
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
    private final com.reazip.economycraft.shop.ShopManager shop;
    private final com.reazip.economycraft.orders.OrderManager orders;
    private final Set<UUID> displayed = new HashSet<>();

    private static final int PLAYTIME_TICKS_PER_HOUR = 20 * 60 * 60;
    private static final int LEADERBOARD_LIMIT = 5;

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

        this.shop = new com.reazip.economycraft.shop.ShopManager(server);
        this.orders = new com.reazip.economycraft.orders.OrderManager(server);

        applyScoreboardSettingOnStartup();
        this.prices = new PriceRegistry(server);
    }

    public MinecraftServer getServer() {
        return server;
    }

    // =====================================================================
    // === Name handling ================================
    // =====================================================================

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

    // =====================================================================
    // === Balances ========================================================
    // =====================================================================

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
        long balance = getBalance(player, true);
        if (balance < amount) return false;
        balances.put(player, clamp(balance - amount));
        updateLeaderboard();
        save();
        return true;
    }

    public boolean pay(UUID from, UUID to, long amount) {
        long balance = getBalance(from, false);
        if (balance < amount) return false;
        removeMoney(from, amount);
        addMoney(to, amount);
        return true;
    }

    // =====================================================================
    // === Load / Save =====================================================
    // =====================================================================

    public void load() {
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                Map<UUID, Double> map = GSON.fromJson(json, new TypeToken<Map<UUID, Double>>(){}.getType());
                if (map != null) {
                    for (Map.Entry<UUID, Double> e : map.entrySet()) {
                        balances.put(e.getKey(), Math.min(e.getValue().longValue(), MAX));
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
                if (map != null) lastDaily.putAll(map);
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

    // =====================================================================
    // === Scoreboard / Leaderboard =======================================
    // =====================================================================

    private void applyScoreboardSettingOnStartup() {
        Scoreboard board = server.getScoreboard();

        if (EconomyConfig.get().scoreboardEnabled) {
            setupObjective();
            return;
        }

        board.setDisplayObjective(DisplaySlot.SIDEBAR, null);

        Objective existing = board.getObjective("eco_balance");
        if (existing != null) {
            board.removeObjective(existing);
        }

        objective = null;
        displayed.clear();
    }

    private void setupObjective() {
        Scoreboard board = server.getScoreboard();
        objective = board.getObjective("eco_balance");

        if (objective == null) {
            objective = board.addObjective(
                    "eco_balance",
                    ObjectiveCriteria.DUMMY,
                    Component.literal("Balance"),
                    ObjectiveCriteria.RenderType.INTEGER,
                    true,
                    null
            );
        }
        board.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        updateLeaderboard();
    }

    private void updateLeaderboard() {
        if (!EconomyConfig.get().scoreboardEnabled) {
            Scoreboard board = server.getScoreboard();
            board.setDisplayObjective(DisplaySlot.SIDEBAR, null);
            if (objective != null) board.removeObjective(objective);
            objective = null;
            displayed.clear();
            return;
        }

        if ("stats".equalsIgnoreCase(EconomyConfig.get().scoreboardMode)) {
            updateStatsScoreboard();
            return;
        }

        Scoreboard board = server.getScoreboard();
        if (objective != null) board.removeObjective(objective);

        objective = board.addObjective(
                "eco_balance",
                ObjectiveCriteria.DUMMY,
                Component.literal("Balance"),
                ObjectiveCriteria.RenderType.INTEGER,
                true,
                null
        );
        board.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        displayed.clear();

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

        for (var e : sorted.stream().limit(LEADERBOARD_LIMIT).toList()) {
            UUID id = e.getKey();
            String name = resolveName(server, id);
            board.getOrCreatePlayerScore(
                    net.minecraft.world.scores.ScoreHolder.forNameOnly(name),
                    objective
            ).set(e.getValue().intValue());
            displayed.add(id);
        }
    }

    private void updateStatsScoreboard() {
        Scoreboard board = server.getScoreboard();
        if (objective != null) board.removeObjective(objective);
        objective = board.addObjective(
                "eco_stats",
                ObjectiveCriteria.DUMMY,
                Component.literal("STATS"),
                ObjectiveCriteria.RenderType.INTEGER,
                true,
                null
        );
        board.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        displayed.clear();

        int score = 5;
        EconomyConfig.ScoreboardStats config = EconomyConfig.get().scoreboardStats;
        List<UUID> targets = server.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID)
                .toList();
        if (targets.isEmpty()) {
            targets = balances.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .toList();
        }

        UUID best = targets.stream().findFirst().orElse(null);
        if (best == null) {
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(best);
        if (config.balance) {
            setScoreLine(board, "Balance: " + EconomyCraft.formatMoney(getBalance(best, true)), score--);
        }
        if (config.deaths) {
            int deaths = player != null
                    ? player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS))
                    : 0;
            setScoreLine(board, "Deaths: " + deaths, score--);
        }
        if (config.playtime) {
            int ticks = player != null
                    ? player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME))
                    : 0;
            long hours = ticks / PLAYTIME_TICKS_PER_HOUR;
            setScoreLine(board, "Playtime: " + hours + "h", score--);
        }
        if (config.team) {
            String teamName = "None";
            if (player != null) {
                Team team = player.getTeam();
                teamName = team != null ? team.getName() : "None";
            }
            setScoreLine(board, "Team: " + teamName, score--);
        }
    }

    private void setScoreLine(Scoreboard board, String line, int score) {
        board.getOrCreatePlayerScore(
                net.minecraft.world.scores.ScoreHolder.forNameOnly(line),
                objective
        ).set(score);
    }

    public boolean toggleScoreboard() {
        Scoreboard board = server.getScoreboard();
        EconomyConfig.get().scoreboardEnabled = !EconomyConfig.get().scoreboardEnabled;
        EconomyConfig.save();

        if (EconomyConfig.get().scoreboardEnabled) {
            setupObjective();
        } else {
            board.setDisplayObjective(DisplaySlot.SIDEBAR, null);
            if (objective != null) {
                board.removeObjective(objective);
                objective = null;
            }
        }

        return EconomyConfig.get().scoreboardEnabled;
    }

    // =====================================================================
    // === Misc ============================================================
    // =====================================================================

    public com.reazip.economycraft.shop.ShopManager getShop() {
        return shop;
    }

    public com.reazip.economycraft.orders.OrderManager getOrders() {
        return orders;
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
        double pct = EconomyConfig.get().pvpBalanceLossPercentage;
        if (pct <= 0.0) return;
        if (victim == null || killer == null) return;
        if (victim.getUUID().equals(killer.getUUID())) return;

        long victimBal = getBalance(victim.getUUID(), true);
        if (victimBal <= 0L) return;

        long loss = (long)Math.floor(pct * victimBal);
        if (loss <= 0L) return;

        removeMoney(victim.getUUID(), loss);
        addMoney(killer.getUUID(), loss);

        victim.sendSystemMessage(Component.literal(
                "You lost " + EconomyCraft.formatMoney(loss) + " for being killed by " + killer.getName().getString())
                .withStyle(net.minecraft.ChatFormatting.RED));

        killer.sendSystemMessage(Component.literal(
                "You received " + EconomyCraft.formatMoney(loss) + " for killing " + victim.getName().getString())
                .withStyle(net.minecraft.ChatFormatting.GREEN));
    }

    private long clamp(long value) {
        return Math.max(0, Math.min(MAX, value));
    }

    private static final class UserCacheEntry {
        String name;
        String uuid;
    }

    private record DailySellData(long day, long amount) {}
}
