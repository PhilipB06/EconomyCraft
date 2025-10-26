package com.reazip.economycraft;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.reazip.economycraft.util.IdentityCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class EconomyManager {
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<UUID, Long>>(){}.getType();

    private final MinecraftServer server;
    private final Path file;
    private final Path dailyFile;

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, Long> lastDaily = new HashMap<>();
    private Map<UUID, String> diskUserCache = null;

    private Objective objective;
    private final com.reazip.economycraft.shop.ShopManager shop;
    private final com.reazip.economycraft.orders.OrderManager orders;
    private final Set<UUID> displayed = new HashSet<>();

    public static final long MAX = 999_999_999L;

    public EconomyManager(MinecraftServer server) {
        this.server = server;
        Path dir = server.getFile("config/economycraft");
        Path dataDir = dir.resolve("data");
        try { Files.createDirectories(dataDir); } catch (IOException ignored) {}

        this.file = dataDir.resolve("balances.json");
        this.dailyFile = dataDir.resolve("daily.json");

        EconomyConfig.load(server);
        load();
        loadDaily();

        this.shop = new com.reazip.economycraft.shop.ShopManager(server);
        this.orders = new com.reazip.economycraft.orders.OrderManager(server);

        if (EconomyConfig.get().scoreboardEnabled) {
            setupObjective();
        }
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

    // =====================================================================
    // === Scoreboard / Leaderboard =======================================
    // =====================================================================

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
        if (!EconomyConfig.get().scoreboardEnabled) return;

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
        sorted.sort(Map.Entry.<UUID, Long>comparingByValue().reversed());

        for (var e : sorted.stream().limit(5).toList()) {
            UUID id = e.getKey();
            String name = resolveName(server, id);
            board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective)
                    .set(e.getValue().intValue());
            displayed.add(id);
        }
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

    public Map<UUID, Long> getBalances() {
        return balances;
    }

    public void removePlayer(UUID id) {
        balances.remove(id);
        updateLeaderboard();
        save();
    }

    public boolean claimDaily(UUID player) {
        long today = java.time.LocalDate.now().toEpochDay();
        long last = lastDaily.getOrDefault(player, -1L);
        if (last == today) return false;
        lastDaily.put(player, today);
        addMoney(player, EconomyConfig.get().dailyAmount);
        return true;
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

    // For reading usercache.json
    private static final class UserCacheEntry {
        String name;
        String uuid;
    }
}
