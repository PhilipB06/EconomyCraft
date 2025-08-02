package com.reazip.economycraft;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public class EconomyManager {
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<UUID, Long>>(){}.getType();

    private final MinecraftServer server;
    private final Path file;
    private final Path dailyFile;
    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, Long> lastDaily = new HashMap<>();
    private Objective objective;
    private final com.reazip.economycraft.shop.ShopManager shop;
    private final com.reazip.economycraft.orders.OrderManager orders;
    private final Set<UUID> displayed = new HashSet<>();

    public static final long MAX = 999_999_999L;

    public EconomyManager(MinecraftServer server) {
        this.server = server;
        Path dir = server.getFile("config/EconomyCraft");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        this.file = dir.resolve("balances.json");
        this.dailyFile = dir.resolve("daily.json");
        EconomyConfig.load(server);
        load();
        loadDaily();
        this.shop = new com.reazip.economycraft.shop.ShopManager(server);
        this.orders = new com.reazip.economycraft.orders.OrderManager(server);
        setupObjective();
    }

    public MinecraftServer getServer() {
        return server;
    }

    public long getBalance(UUID player) {
        return balances.computeIfAbsent(player, id -> clamp(EconomyConfig.get().startingBalance));
    }

    public void addMoney(UUID player, long amount) {
        balances.put(player, clamp(getBalance(player) + amount));
        updateLeaderboard();
        save();
    }

    public void setMoney(UUID player, long amount) {
        balances.put(player, clamp(amount));
        updateLeaderboard();
        save();
    }

    public boolean pay(UUID from, UUID to, long amount) {
        long balance = getBalance(from);
        if (balance < amount) return false;
        setMoney(from, balance - amount);
        addMoney(to, amount);
        return true;
    }

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
            } catch (IOException ignored) {
            }
        }
    }

    public void save() {
        try {
            Map<UUID, Long> data = new HashMap<>(balances);
            String json = GSON.toJson(data, TYPE);
            Files.writeString(file, json);
        } catch (IOException ignored) {
        }
        try {
            String json = GSON.toJson(lastDaily, new TypeToken<Map<UUID, Long>>(){}.getType());
            Files.writeString(dailyFile, json);
        } catch (IOException ignored) {}
    }

    public Map<UUID, Long> getBalances() {
        return balances;
    }

    public void removePlayer(UUID id) {
        balances.remove(id);
        updateLeaderboard();
        save();
    }

    public com.reazip.economycraft.shop.ShopManager getShop() {
        return shop;
    }

    public com.reazip.economycraft.orders.OrderManager getOrders() {
        return orders;
    }

    public boolean claimDaily(UUID player) {
        long today = java.time.LocalDate.now().toEpochDay();
        long last = lastDaily.getOrDefault(player, -1L);
        if (last == today) return false;
        lastDaily.put(player, today);
        addMoney(player, EconomyConfig.get().dailyAmount);
        return true;
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

    private void setupObjective() {
        Scoreboard board = server.getScoreboard();
        objective = board.getObjective("eco_balance");
        net.minecraft.network.chat.numbers.NumberFormat fmt = new net.minecraft.network.chat.numbers.NumberFormat() {
            @Override
            public net.minecraft.network.chat.MutableComponent format(int value) {
                return Component.literal(Integer.toString(value));
            }

            @Override
            public net.minecraft.network.chat.numbers.NumberFormatType<?> type() {
                return net.minecraft.network.chat.numbers.StyledFormat.TYPE;
            }
        };
        if (objective == null) {
            objective = board.addObjective("eco_balance", ObjectiveCriteria.DUMMY, Component.literal("Balance"), ObjectiveCriteria.RenderType.INTEGER, true, fmt);
        } else {
            objective.setNumberFormat(fmt);
        }
        board.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        updateLeaderboard();
    }

    private void updateLeaderboard() {
        Scoreboard board = server.getScoreboard();
        if (objective != null) {
            board.removeObjective(objective);
        }
        net.minecraft.network.chat.numbers.NumberFormat fmt = new net.minecraft.network.chat.numbers.NumberFormat() {
            @Override
            public net.minecraft.network.chat.MutableComponent format(int value) {
                return Component.literal(Integer.toString(value));
            }

            @Override
            public net.minecraft.network.chat.numbers.NumberFormatType<?> type() {
                return net.minecraft.network.chat.numbers.StyledFormat.TYPE;
            }
        };
        objective = board.addObjective("eco_balance", ObjectiveCriteria.DUMMY, Component.literal("Balance"), ObjectiveCriteria.RenderType.INTEGER, true, fmt);
        board.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        displayed.clear();
        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(balances.entrySet());
        sorted.sort(Map.Entry.<UUID, Long>comparingByValue().reversed());
        for (Map.Entry<UUID, Long> e : sorted.stream().limit(5).toList()) {
            String name = server.getProfileCache().get(e.getKey()).map(p -> p.getName()).orElse(e.getKey().toString());
            board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective).set(e.getValue().intValue());
            displayed.add(e.getKey());
        }
    }

    private long clamp(long value) {
        return Math.max(0, Math.min(MAX, value));
    }
}
