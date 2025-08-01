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

public class EconomyManager {
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<UUID, Long>>(){}.getType();

    private final MinecraftServer server;
    private final Path file;
    private final Map<UUID, Long> balances = new HashMap<>();
    private Objective objective;
    private final com.reazip.economycraft.shop.ShopManager shop;
    private final com.reazip.economycraft.market.MarketManager market;

    public EconomyManager(MinecraftServer server) {
        this.server = server;
        this.file = server.getFile("economycraft_balances.json");
        load();
        this.shop = new com.reazip.economycraft.shop.ShopManager(server);
        this.market = new com.reazip.economycraft.market.MarketManager(server);
        setupObjective();
    }

    public MinecraftServer getServer() {
        return server;
    }

    public long getBalance(UUID player) {
        return balances.getOrDefault(player, 0L);
    }

    public void addMoney(UUID player, long amount) {
        balances.put(player, getBalance(player) + amount);
        updateLeaderboard();
    }

    public void setMoney(UUID player, long amount) {
        balances.put(player, amount);
        updateLeaderboard();
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
                        balances.put(e.getKey(), e.getValue().longValue());
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
    }

    public Map<UUID, Long> getBalances() {
        return balances;
    }

    public com.reazip.economycraft.shop.ShopManager getShop() {
        return shop;
    }

    public com.reazip.economycraft.market.MarketManager getMarket() {
        return market;
    }

    private void setupObjective() {
        Scoreboard board = server.getScoreboard();
        objective = board.getObjective("eco_balance");
        if (objective == null) {
            objective = board.addObjective("eco_balance", ObjectiveCriteria.DUMMY, Component.literal("Balance"), ObjectiveCriteria.RenderType.INTEGER);
        }
        board.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        updateLeaderboard();
    }

    private void updateLeaderboard() {
        if (objective == null) return;
        // sort balances
        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(balances.entrySet());
        sorted.sort(Map.Entry.<UUID, Long>comparingByValue().reversed());

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            net.minecraft.server.ServerScoreboard board = new net.minecraft.server.ServerScoreboard(server);
            Objective obj = board.addObjective("eco_balance", ObjectiveCriteria.DUMMY, Component.literal("Balance"), ObjectiveCriteria.RenderType.INTEGER);
            board.setDisplayObjective(DisplaySlot.SIDEBAR, obj);

            int rank = 1;
            for (Map.Entry<UUID, Long> e : sorted.stream().limit(5).toList()) {
                String name = server.getProfileCache().get(e.getKey()).map(p -> p.getName()).orElse(e.getKey().toString());
                board.getOrCreatePlayerScore(name, obj).setScore(e.getValue().intValue());
                rank++;
            }

            int viewerRank = -1;
            long viewerBal = getBalance(viewer.getUUID());
            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).getKey().equals(viewer.getUUID())) {
                    viewerRank = i + 1;
                    break;
                }
            }

            if (viewerRank > 5) {
                board.getOrCreatePlayerScore("-----", obj).setScore(0);
                board.getOrCreatePlayerScore(viewer.getName().getString() + " #" + viewerRank, obj).setScore((int) viewerBal);
            }

            viewer.setScoreboard(board);
        }
    }
}
